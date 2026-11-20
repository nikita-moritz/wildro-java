package app.wildro.event.internal;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import app.wildro.event.audit.AuditAction;
import app.wildro.event.audit.AuditChannel;
import app.wildro.event.audit.AuditRecorder;
import app.wildro.event.audit.Operator;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;

/**
 * Carries out in this module what other modules decided (SPEC.md §18.4): late if
 * they or we were down, but without loss and without repeats. Each entry of the
 * user and community services' outboxes is applied, and this service's cursor
 * moved past it, in one transaction.
 *
 * <ul>
 * <li>user.erased: the person's future personal events are cancelled, and every
 * event they wrote stops showing their name (§18.1).</li>
 * <li>community.closed: the community's future events are cancelled; pages stay
 * at their addresses, marked cancelled (§18.3).</li>
 * <li>community.suspended: the owner's statement of reasons (DSA Art. 17) is
 * queued for the notice relay, which already sends those of events.</li>
 * </ul>
 */
@Component
public class OutboxConsumers {

	private static final Logger log = LoggerFactory.getLogger(OutboxConsumers.class);

	private static final Operator SYSTEM = new Operator("system", "consumer", AuditChannel.WEB);

	private final JdbcClient jdbc;

	private final EventRepository events;

	private final AuditRecorder audit;

	private final TransactionTemplate transactions;

	private final Clock clock;

	private final CacheInvalidator cache;

	private final RestClient users;

	private final RestClient communities;

	private final boolean usersConfigured;

	private final boolean communitiesConfigured;

	OutboxConsumers(RestClient.Builder builder, JdbcClient jdbc, EventRepository events, AuditRecorder audit,
			TransactionTemplate transactions, Clock clock, CacheInvalidator cache,
			@Value("${wildro.users.base-url:}") String usersUrl,
			@Value("${wildro.communities.base-url:}") String communitiesUrl,
			@Value("${wildro.internal-token:}") String token) {
		this.jdbc = jdbc;
		this.events = events;
		this.audit = audit;
		this.transactions = transactions;
		this.clock = clock;
		this.cache = cache;
		boolean tokenSet = token.length() >= 32;
		this.usersConfigured = tokenSet && !usersUrl.isBlank();
		this.communitiesConfigured = tokenSet && !communitiesUrl.isBlank();
		this.users = client(builder.clone(), usersConfigured ? usersUrl : "http://127.0.0.1:1", token);
		this.communities = client(builder.clone(), communitiesConfigured ? communitiesUrl : "http://127.0.0.1:1", token);
	}

	private static RestClient client(RestClient.Builder builder, String baseUrl, String token) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
		factory.setReadTimeout(Duration.ofSeconds(10));
		return builder.baseUrl(baseUrl).defaultHeader("X-Internal-Token", token).requestFactory(factory).build();
	}

	@Scheduled(fixedDelayString = "${wildro.consumer.delay}", initialDelayString = "${wildro.consumer.initial-delay}")
	void poll() {
		if (this.usersConfigured) {
			pull("user-service", this.users);
		}
		if (this.communitiesConfigured) {
			pull("community-service", this.communities);
		}
	}

	private void pull(String source, RestClient client) {
		try {
			List<Entry> entries = client.get()
				.uri("/internal/outbox?after={after}&limit=100", cursor(source))
				.retrieve()
				.body(new ParameterizedTypeReference<List<Entry>>() {
				});
			consume(source, entries == null ? List.of() : entries);
		}
		catch (RestClientException ex) {
			log.warn("{} outbox unreachable, retrying later: {}", source, ex.getMessage());
		}
	}

	/**
	 * Applies each entry and moves the cursor past it, one transaction per entry,
	 * then tells the frontend which pages went stale, once the change committed.
	 */
	public void consume(String source, List<Entry> entries) {
		for (Entry entry : entries) {
			List<String> touched = this.transactions.execute((status) -> {
				List<String> slugs = switch (entry.type()) {
					case "user.erased" -> userErased(entry.subject());
					case "community.closed" -> communityClosed(Long.parseLong(entry.subject()));
					case "community.suspended" -> communitySuspended(entry.payload());
					// Unknown types are skipped, so a producer can add one without breaking us.
					default -> List.of();
				};
				this.jdbc.sql("""
						INSERT INTO consumer_cursor (source, last_id) VALUES (:s, :id)
						ON CONFLICT (source) DO UPDATE SET last_id = EXCLUDED.last_id
						""").param("s", source).param("id", entry.id()).update();
				return slugs;
			});
			if (touched != null && !touched.isEmpty()) {
				List<String> tags = new ArrayList<>();
				tags.add("events:feed");
				touched.forEach((slug) -> tags.add("event:" + slug));
				this.cache.invalidate(tags);
			}
		}
	}

	long cursor(String source) {
		return this.jdbc.sql("SELECT last_id FROM consumer_cursor WHERE source = :s")
			.param("s", source)
			.query(Long.class)
			.optional()
			.orElse(0L);
	}

	/** @return the events whose page changed */
	private List<String> userErased(String userId) {
		Instant now = this.clock.instant();
		List<String> touched = new ArrayList<>();
		for (Event event : this.events.findAll((root, query, cb) -> cb.or(cb.equal(root.get("ownerUserId"), userId),
				cb.equal(root.get("authorUserId"), userId)))) {
			if (userId.equals(event.getOwnerUserId()) && !event.hasPassed(now)
					&& (event.getStatus() == EventStatus.DRAFT || event.getStatus() == EventStatus.PUBLISHED)) {
				event.cancel();
				this.audit.record(SYSTEM, AuditAction.EVENT_CANCELLED, event, Map.of("cause", "owner account deleted"));
			}
			event.eraseAuthor();
			touched.add(event.getSlug());
		}
		return touched;
	}

	/** @return the events whose page changed */
	private List<String> communityClosed(long communityId) {
		Instant now = this.clock.instant();
		List<String> touched = new ArrayList<>();
		for (Event event : this.events.findAll((root, query, cb) -> cb.equal(root.get("ownerCommunityId"), communityId))) {
			if (!event.hasPassed(now)
					&& (event.getStatus() == EventStatus.DRAFT || event.getStatus() == EventStatus.PUBLISHED)) {
				event.cancel();
				this.audit.record(SYSTEM, AuditAction.EVENT_CANCELLED, event, Map.of("cause", "community closed"));
				touched.add(event.getSlug());
			}
		}
		return touched;
	}

	/**
	 * Queues the owner's statement of reasons for the relay that sends the event
	 * notices. The payload is stored as it came: owner, group name, language and
	 * reason. No page of this service changes.
	 */
	private List<String> communitySuspended(String payload) {
		this.jdbc.sql("""
				INSERT INTO notice_outbox (kind, recipient_user_id, payload)
				SELECT 'COMMUNITY_SUSPENDED', p->>'ownerUserId', p
				FROM (SELECT CAST(:payload AS jsonb) AS p) AS entry
				WHERE p->>'ownerUserId' IS NOT NULL
				""").param("payload", payload).update();
		return List.of();
	}

	public record Entry(long id, String type, String subject, String payload, String createdAt) {
	}

}
