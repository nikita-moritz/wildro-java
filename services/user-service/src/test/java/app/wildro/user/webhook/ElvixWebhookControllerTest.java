package app.wildro.user.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import app.wildro.user.FakeDirectory;
import app.wildro.user.MutableClock;
import app.wildro.user.TestcontainersConfiguration;
import app.wildro.user.identity.Identity;

/** What elvix reports, signed the way elvix signs it, and what becomes of it here. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ElvixWebhookControllerTest {

	private static final String SECRET = "whsec_test_secret";

	private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private FakeDirectory directory;

	@BeforeEach
	void clean() {
		this.jdbc.sql("DELETE FROM users").update();
		this.jdbc.sql("DELETE FROM processed_webhook").update();
		this.jdbc.sql("DELETE FROM outbox").update();
		this.directory.reset();
		this.clock.set(NOW);
		this.jdbc.sql("INSERT INTO users (id, email, display_name) VALUES ('u1', 'ada@example.org', 'Ada')").update();
	}

	@Test
	void aBlockIsRecorded() throws Exception {
		deliver(event("e1", "user.banned", NOW.minusSeconds(5), "u1")).andExpect(status().isNoContent());
		assertThat(column("status")).isEqualTo("BANNED");
	}

	@Test
	void anUnsignedOrForgedDeliveryChangesNothing() throws Exception {
		String body = event("e1", "user.banned", NOW, "u1");
		this.mvc.perform(post("/v1/webhooks/elvix").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnauthorized());
		this.mvc.perform(post("/v1/webhooks/elvix").contentType(MediaType.APPLICATION_JSON)
			.header("Elvix-Signature", "t=" + NOW.getEpochSecond() + ",v1=" + "0".repeat(64))
			.content(body)).andExpect(status().isUnauthorized());
		assertThat(column("status")).isEqualTo("ACTIVE");
	}

	@Test
	void aCapturedDeliveryIsUselessOnceTheToleranceHasPassed() throws Exception {
		String body = event("e1", "user.banned", NOW, "u1");
		String signature = sign(body, NOW);
		this.clock.advance(Duration.ofMinutes(6));
		this.mvc.perform(post("/v1/webhooks/elvix").contentType(MediaType.APPLICATION_JSON)
			.header("Elvix-Signature", signature)
			.content(body)).andExpect(status().isUnauthorized());
	}

	@Test
	void deletionErasesThePersonOnceAndLeavesWorkForTheOtherModules() throws Exception {
		String body = event("e1", "user.deleted", NOW.minusSeconds(5), "u1");
		deliver(body).andExpect(status().isNoContent());
		deliver(body).andExpect(status().isNoContent());

		assertThat(column("email")).isNull();
		assertThat(column("display_name")).isNull();
		assertThat(column("status")).isEqualTo("DELETED");
		assertThat(this.jdbc.sql("SELECT count(*) FROM outbox WHERE type = 'user.erased' AND user_id = 'u1'")
			.query(Integer.class)
			.single()).isEqualTo(1);
	}

	@Test
	void aLateDeliveryDoesNotOverwriteANewerStatus() throws Exception {
		deliver(event("e2", "user.unbanned", NOW.minusSeconds(10), "u1")).andExpect(status().isNoContent());
		deliver(event("e1", "user.banned", NOW.minusSeconds(20), "u1")).andExpect(status().isNoContent());
		assertThat(column("status")).isEqualTo("ACTIVE");
	}

	@Test
	void aChangedAddressIsReadAgainFromElvix() throws Exception {
		this.directory.put(new Identity("u1", "ada@new.example.org", "Ada"));
		String body = """
				{"id":"e1","type":"user.profile_updated","createdAt":"%s","applicationId":"app_wildro_test",
				 "actorUserId":"u1","data":{"userId":"u1","email":"stale@example.org","name":"Ada","categories":["identity"]}}
				""".formatted(NOW);
		deliver(body).andExpect(status().isNoContent());
		assertThat(column("email")).isEqualTo("ada@new.example.org");
	}

	@Test
	void anotherApplicationsEventIsRefused() throws Exception {
		String body = event("e1", "user.banned", NOW, "u1").replace("app_wildro_test", "someone_else");
		deliver(body).andExpect(status().isBadRequest());
		assertThat(column("status")).isEqualTo("ACTIVE");
	}

	@Test
	void anEventTypeWeDoNotKnowIsAcknowledgedAndIgnored() throws Exception {
		deliver(event("e1", "user.something_new", NOW, "u1")).andExpect(status().isNoContent());
	}

	private ResultActions deliver(String body) throws Exception {
		return this.mvc.perform(post("/v1/webhooks/elvix").contentType(MediaType.APPLICATION_JSON)
			.header("Elvix-Signature", sign(body, this.clock.instant()))
			.content(body));
	}

	private static String sign(String body, Instant at) {
		long t = at.getEpochSecond();
		return "t=" + t + ",v1=" + WebhookSignature.hmac(SECRET, t + "." + body);
	}

	private static String event(String id, String type, Instant createdAt, String userId) {
		return """
				{"id":"%s","type":"%s","createdAt":"%s","applicationId":"app_wildro_test","actorUserId":null,
				 "data":{"userId":"%s","email":"ada@example.org","name":"Ada","status":"banned","reason":null}}
				""".formatted(id, type, createdAt, userId);
	}

	private String column(String name) {
		// A row with a null column comes back as an empty Optional rather than a null.
		return this.jdbc.sql("SELECT " + name + " FROM users WHERE id = 'u1'").query(String.class).optional().orElse(null);
	}

}
