package app.wildro.event.moderation.notice;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Sends what the outbox holds. The suspension wrote its notice in its own
 * transaction; this delivers it afterwards, so a mail transport that is down
 * delays the notice and loses nothing. Rows are claimed with SKIP LOCKED, so
 * two runs never send the same one.
 */
@Component
public class NoticeRelay {

	static final int MAX_ATTEMPTS = 8;

	private static final Logger log = LoggerFactory.getLogger(NoticeRelay.class);

	private final JdbcClient jdbc;

	private final Mailer mailer;

	private final ContactDirectory contacts;

	private final JsonMapper json;

	private final Clock clock;

	private final String siteUrl;

	NoticeRelay(JdbcClient jdbc, Mailer mailer, ContactDirectory contacts, JsonMapper json, Clock clock,
			@Value("${wildro.site-url}") String siteUrl) {
		this.jdbc = jdbc;
		this.mailer = mailer;
		this.contacts = contacts;
		this.json = json;
		this.clock = clock;
		this.siteUrl = siteUrl.replaceAll("/+$", "");
	}

	@Scheduled(fixedDelayString = "${wildro.mail.relay-delay}", initialDelayString = "${wildro.mail.relay-initial-delay}")
	void scheduled() {
		deliver();
	}

	/** @return how many notices went out in this run */
	@Transactional
	public int deliver() {
		if (!this.mailer.enabled()) {
			return 0;
		}
		Instant now = this.clock.instant();
		List<Pending> pending = this.jdbc.sql("""
				SELECT n.id, n.kind, n.recipient_user_id, n.payload::text AS payload, n.attempts,
				       coalesce(e.original_locale, n.payload->>'locale') AS locale,
				       coalesce(t.name, n.payload->>'name') AS name
				FROM notice_outbox n
				-- An event notice names its event; a group notice carries name and language itself.
				LEFT JOIN events e ON e.id = n.event_id
				LEFT JOIN event_translations t ON t.event_id = e.id AND t.locale = coalesce(e.original_locale, 'de')
				WHERE n.sent_at IS NULL AND n.abandoned_at IS NULL
				  AND (n.next_attempt_at IS NULL OR n.next_attempt_at <= :now)
				ORDER BY n.id
				LIMIT 20
				FOR UPDATE OF n SKIP LOCKED
				""")
			.param("now", Timestamp.from(now))
			.query((rs, i) -> new Pending(rs.getLong("id"), rs.getString("kind"), rs.getString("recipient_user_id"),
					rs.getString("payload"), rs.getInt("attempts"), rs.getString("locale"), rs.getString("name")))
			.list();
		int sent = 0;
		for (Pending notice : pending) {
			if (notice.name() == null) {
				abandon(notice.id(), "what the notice is about no longer exists", now);
				continue;
			}
			try {
				Optional<String> to = this.contacts.emailOf(notice.recipient());
				if (to.isEmpty()) {
					abandon(notice.id(), "the recipient's account no longer exists", now);
					continue;
				}
				String reason = this.json.readTree(notice.payload()).get("reason").asString();
				String terms = this.siteUrl + "/" + (notice.locale() == null ? "de" : notice.locale()) + "/terms";
				NoticeText.Mail mail = "COMMUNITY_SUSPENDED".equals(notice.kind())
						? NoticeText.communitySuspended(notice.locale(), notice.name(), terms, reason)
						: NoticeText.suspended(notice.locale(), notice.name(), terms, reason);
				this.mailer.send(to.get(), mail.subject(), mail.text());
				this.jdbc.sql("UPDATE notice_outbox SET sent_at = :at, attempts = attempts + 1 WHERE id = :id")
					.param("at", Timestamp.from(now))
					.param("id", notice.id())
					.update();
				sent++;
			}
			catch (MailFailedException ex) {
				retryLater(notice, ex.getMessage(), now);
			}
		}
		return sent;
	}

	private void retryLater(Pending notice, String error, Instant now) {
		int attempts = notice.attempts() + 1;
		if (attempts >= MAX_ATTEMPTS) {
			abandon(notice.id(), error, now);
			log.warn("Gave up on notice {} after {} attempts: {}", notice.id(), attempts, error);
			return;
		}
		this.jdbc.sql("""
				UPDATE notice_outbox SET attempts = :attempts, last_error = :error, next_attempt_at = :next
				WHERE id = :id
				""")
			.param("attempts", attempts)
			.param("error", error)
			.param("next", Timestamp.from(now.plus(Duration.ofMinutes(1L << attempts))))
			.param("id", notice.id())
			.update();
	}

	private void abandon(long id, String error, Instant now) {
		this.jdbc.sql("UPDATE notice_outbox SET abandoned_at = :at, last_error = :error WHERE id = :id")
			.param("at", Timestamp.from(now))
			.param("error", error)
			.param("id", id)
			.update();
	}

	record Pending(long id, String kind, String recipient, String payload, int attempts, String locale, String name) {
	}

}
