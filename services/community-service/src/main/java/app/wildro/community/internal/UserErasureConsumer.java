package app.wildro.community.internal;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

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

import app.wildro.community.community.CommunityService;

/**
 * Pulls user.erased from the user service's outbox (SPEC.md §18.1, §18.4). Each
 * entry is applied and the cursor moved in one transaction: a crash between the
 * two is impossible, a user service that is down only delays the consequence.
 */
@Component
class UserErasureConsumer {

	static final String SOURCE = "user-service";

	private static final Logger log = LoggerFactory.getLogger(UserErasureConsumer.class);

	private final RestClient client;

	private final JdbcClient jdbc;

	private final CommunityService communities;

	private final TransactionTemplate transactions;

	private final boolean configured;

	UserErasureConsumer(RestClient.Builder builder, JdbcClient jdbc, CommunityService communities,
			TransactionTemplate transactions, @Value("${wildro.users.base-url:}") String baseUrl,
			@Value("${wildro.internal-token:}") String token) {
		this.jdbc = jdbc;
		this.communities = communities;
		this.transactions = transactions;
		this.configured = !baseUrl.isBlank() && token.length() >= 32;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
		factory.setReadTimeout(Duration.ofSeconds(10));
		this.client = builder.baseUrl(this.configured ? baseUrl : "http://127.0.0.1:1")
			.defaultHeader("X-Internal-Token", token)
			.requestFactory(factory)
			.build();
	}

	@Scheduled(fixedDelayString = "${wildro.consumer.delay}", initialDelayString = "${wildro.consumer.initial-delay}")
	void poll() {
		if (!this.configured) {
			return;
		}
		try {
			consume(fetch(cursor()));
		}
		catch (RestClientException ex) {
			log.warn("user service outbox unreachable, retrying later: {}", ex.getMessage());
		}
	}

	List<Entry> fetch(long after) {
		List<Entry> entries = this.client.get()
			.uri("/internal/outbox?after={after}&limit=100", after)
			.retrieve()
			.body(new ParameterizedTypeReference<List<Entry>>() {
			});
		return entries == null ? List.of() : entries;
	}

	/** Applies each entry and moves the cursor past it, one transaction per entry. */
	void consume(List<Entry> entries) {
		for (Entry entry : entries) {
			this.transactions.executeWithoutResult((status) -> {
				if ("user.erased".equals(entry.type())) {
					this.communities.erase(entry.subject());
				}
				this.jdbc.sql("""
						INSERT INTO consumer_cursor (source, last_id) VALUES (:s, :id)
						ON CONFLICT (source) DO UPDATE SET last_id = EXCLUDED.last_id
						""").param("s", SOURCE).param("id", entry.id()).update();
			});
		}
	}

	long cursor() {
		return this.jdbc.sql("SELECT last_id FROM consumer_cursor WHERE source = :s")
			.param("s", SOURCE)
			.query(Long.class)
			.optional()
			.orElse(0L);
	}

	record Entry(long id, String type, String subject, String payload, String createdAt) {
	}

}
