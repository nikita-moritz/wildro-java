package app.wildro.event.moderation.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import app.wildro.event.TestcontainersConfiguration;
import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.EventTranslation;
import app.wildro.event.domain.GeoPoints;
import app.wildro.event.internal.OutboxConsumers;
import app.wildro.event.moderation.ModerationService;
import app.wildro.event.own.OwnTestConfiguration;

/** The statement of reasons leaves the outbox once, in the event's language, and survives a failing transport. */
@Import({ TestcontainersConfiguration.class, OwnTestConfiguration.class, NoticeRelayTest.Fakes.class })
@SpringBootTest(properties = "wildro.mail.relay-initial-delay=PT1H")
class NoticeRelayTest {

	@Autowired
	private NoticeRelay relay;

	@Autowired
	private ModerationService moderation;

	@Autowired
	private EventRepository events;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private OutboxConsumers consumers;

	@Autowired
	private FakeMailer mailer;

	@Autowired
	private FakeContacts contacts;

	@BeforeEach
	void clean() {
		this.jdbc.sql("DELETE FROM notice_outbox").update();
		this.jdbc.sql("DELETE FROM consumer_cursor").update();
		this.jdbc.sql("DELETE FROM reports").update();
		this.jdbc.sql("DELETE FROM audit_log").update();
		this.jdbc.sql("DELETE FROM events").update();
		this.mailer.sent.clear();
		this.mailer.failing = false;
		this.mailer.enabled = true;
		this.contacts.emails.clear();
	}

	@Test
	void theOwnerGetsTheReasonInTheEventsLanguageExactlyOnce() {
		this.contacts.emails.put("owner-1", "ada@example.org");
		suspend(ownEvent("tour", "ru"), "Реклама, а не поход.");

		assertThat(this.relay.deliver()).isEqualTo(1);
		assertThat(this.relay.deliver()).isZero();

		assertThat(this.mailer.sent).hasSize(1);
		FakeMailer.Sent mail = this.mailer.sent.get(0);
		assertThat(mail.to()).isEqualTo("ada@example.org");
		assertThat(mail.subject()).startsWith("Твоё событие на wildro снято");
		assertThat(mail.text()).contains("Реклама, а не поход.").contains("/ru/terms").doesNotContain("—");
	}

	@Test
	void aFailingTransportIsRetriedLaterNotLost() {
		this.contacts.emails.put("owner-1", "ada@example.org");
		suspend(ownEvent("tour", "de"), "Spam");
		this.mailer.failing = true;

		assertThat(this.relay.deliver()).isZero();
		assertThat(this.jdbc.sql("SELECT attempts FROM notice_outbox").query(Integer.class).single()).isEqualTo(1);
		// Not due yet: the next attempt waits.
		this.mailer.failing = false;
		assertThat(this.relay.deliver()).isZero();

		this.jdbc.sql("UPDATE notice_outbox SET next_attempt_at = now() - interval '1 second'").update();
		assertThat(this.relay.deliver()).isEqualTo(1);
	}

	@Test
	void aDeletedAccountGetsNoMailAndTheNoticeIsClosed() {
		suspend(ownEvent("tour", "de"), "Spam");
		assertThat(this.relay.deliver()).isZero();
		assertThat(this.jdbc.sql("SELECT abandoned_at IS NOT NULL FROM notice_outbox").query(Boolean.class).single()).isTrue();
		assertThat(this.mailer.sent).isEmpty();
	}

	@Test
	void withoutATransportNoticesWait() {
		this.mailer.enabled = false;
		this.contacts.emails.put("owner-1", "ada@example.org");
		suspend(ownEvent("tour", "de"), "Spam");
		assertThat(this.relay.deliver()).isZero();
		assertThat(this.jdbc.sql("SELECT attempts FROM notice_outbox").query(Integer.class).single()).isZero();
	}

	@Test
	void aSuspendedGroupsOwnerGetsTheReasonInTheGroupsLanguageExactlyOnce() {
		this.contacts.emails.put("owner-1", "ada@example.org");
		var entry = new OutboxConsumers.Entry(41, "community.suspended", "7",
				"{\"ownerUserId\":\"owner-1\",\"name\":\"Trailrunner Aachen\",\"locale\":\"en\",\"reason\":\"Ads, not hikes.\"}",
				Instant.now().toString());
		this.consumers.consume("community-service", List.of(entry));
		// Applied together with the cursor: the next pull starts after it.
		assertThat(this.jdbc.sql("SELECT last_id FROM consumer_cursor WHERE source = 'community-service'")
			.query(Long.class)
			.single()).isEqualTo(41);

		assertThat(this.relay.deliver()).isEqualTo(1);
		assertThat(this.relay.deliver()).isZero();
		FakeMailer.Sent mail = this.mailer.sent.get(0);
		assertThat(mail.to()).isEqualTo("ada@example.org");
		assertThat(mail.subject()).isEqualTo("Your group on wildro was removed: Trailrunner Aachen");
		assertThat(mail.text()).contains("Ads, not hikes.").contains("/en/terms").doesNotContain("—").doesNotContain("–");
	}

	@Test
	void aGroupWhosePictureWasTakenDownTellsItsOwnerWhy() {
		this.contacts.emails.put("owner-1", "ada@example.org");
		this.consumers.consume("community-service", List.of(new OutboxConsumers.Entry(42, "community.logo_removed", "7",
				"{\"ownerUserId\":\"owner-1\",\"name\":\"Trailrunner Aachen\",\"locale\":\"de\",\"reason\":\"Erkennbare Personen.\"}",
				Instant.now().toString())));

		assertThat(this.relay.deliver()).isEqualTo(1);
		FakeMailer.Sent mail = this.mailer.sent.get(0);
		assertThat(mail.subject()).isEqualTo("Das Bild deiner Gruppe auf wildro wurde entfernt: Trailrunner Aachen");
		assertThat(mail.text()).contains("Erkennbare Personen.").contains("/de/terms").doesNotContain("—").doesNotContain("–");
	}

	@Test
	void aNoticeAboutAnEventThatNoLongerExistsIsClosedNotSentAsNull() {
		this.contacts.emails.put("owner-1", "ada@example.org");
		suspend(ownEvent("tour", "de"), "Spam");
		this.jdbc.sql("DELETE FROM events").update();

		assertThat(this.relay.deliver()).isZero();
		assertThat(this.mailer.sent).isEmpty();
		assertThat(this.jdbc.sql("SELECT abandoned_at IS NOT NULL FROM notice_outbox").query(Boolean.class).single()).isTrue();
	}

	private void suspend(Event event, String reason) {
		this.moderation.suspend(event.getSlug(), reason, "m1");
	}

	private Event ownEvent(String slug, String locale) {
		EventTranslation text = new EventTranslation("Tour " + slug, "Aachen", "Beschreibung.");
		return this.events.saveAndFlush(Event.builder()
			.slug(slug)
			.ownedBy("owner-1", locale)
			.status(EventStatus.PUBLISHED)
			.publishedAt(Instant.now())
			.activityType(ActivityType.HIKING)
			.startsAt(Instant.now().plus(Duration.ofDays(3)))
			.startPoint(GeoPoints.of(50.77, 6.08))
			.organizer("Ada")
			.translation("de", text)
			.translation("en", text)
			.translation("ru", text)
			.build());
	}

	static class FakeMailer implements Mailer {

		record Sent(String to, String subject, String text) {
		}

		final List<Sent> sent = new ArrayList<>();

		volatile boolean failing;

		volatile boolean enabled = true;

		@Override
		public boolean enabled() {
			return this.enabled;
		}

		@Override
		public void send(String to, String subject, String text) {
			if (this.failing) {
				throw new MailFailedException("down in this test", null);
			}
			this.sent.add(new Sent(to, subject, text));
		}

	}

	static class FakeContacts implements ContactDirectory {

		final Map<String, String> emails = new ConcurrentHashMap<>();

		@Override
		public Optional<String> emailOf(String userId) {
			return Optional.ofNullable(this.emails.get(userId));
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Fakes {

		@Bean
		@Primary
		FakeMailer fakeMailer() {
			return new FakeMailer();
		}

		@Bean
		@Primary
		FakeContacts fakeContacts() {
			return new FakeContacts();
		}

	}

}
