package app.wildro.event.own;

import static app.wildro.event.own.People.moderator;
import static app.wildro.event.own.People.person;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import app.wildro.event.TestcontainersConfiguration;
import app.wildro.event.media.CoverFiles;

/**
 * The organiser's own picture of an event (decided 2026-11-22): who sets it,
 * what is taken, that an edit keeps it, and moderation taking it alone down.
 * Not transactional: the old file goes only after a commit, which a test that
 * rolls back would never see.
 */
@Import({ TestcontainersConfiguration.class, OwnTestConfiguration.class })
@SpringBootTest(properties = "wildro.mail.relay-initial-delay=PT1H")
@AutoConfigureMockMvc
class OwnEventCoverTest {

	private static final Instant IN_A_WEEK = Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.HOURS);

	@TempDir
	static Path media;

	@DynamicPropertySource
	static void mediaFolder(DynamicPropertyRegistry registry) {
		registry.add("wildro.media.dir", () -> media.toString());
		registry.add("wildro.media.base-url", () -> "https://java.wildro.app/media/events");
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private FakeWriters writers;

	@Autowired
	private JdbcClient jdbc;

	private String slug;

	@BeforeEach
	void reset() throws Exception {
		this.jdbc.sql("DELETE FROM notice_outbox").update();
		this.jdbc.sql("DELETE FROM reports").update();
		this.jdbc.sql("DELETE FROM audit_log").update();
		this.jdbc.sql("DELETE FROM events").update();
		this.writers.reset();
		for (Path file : files()) {
			Files.delete(file);
		}
		String json = this.mvc.perform(post("/v1/own/events").with(person("u1"))
			.param("publish", "true")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body("Sonntagsrunde"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		this.slug = json.replaceAll(".*\"slug\":\"([^\"]+)\".*", "$1");
	}

	@Test
	void theOwnerSetsAPictureThePublicSeesItAndAnEditKeepsIt() throws Exception {
		upload("u1", webp("one")).andExpect(status().isOk())
			.andExpect(jsonPath("$.coverImageUrl").value(matchesPattern("https://java\\.wildro\\.app/media/events/\\d+-[0-9a-f]{12}\\.webp")));
		assertThat(files()).hasSize(1);
		this.mvc.perform(get("/v1/events/" + this.slug).param("locale", "en"))
			.andExpect(jsonPath("$.coverImageUrl").isString())
			.andExpect(jsonPath("$.coverCredit").doesNotExist());

		this.mvc.perform(put("/v1/own/events/" + this.slug).with(person("u1"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body("Sonntagsrunde, neuer Treffpunkt"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.coverImageUrl").isString());
		assertThat(count("audit_log WHERE action = 'COVER_CHANGED' AND operator = 'u1'")).isEqualTo(1);
	}

	@Test
	void aNewPictureReplacesTheOldFileAndRemovingBringsTheStandInBack() throws Exception {
		upload("u1", webp("one"));
		upload("u1", webp("two"));
		assertThat(files()).hasSize(1);
		this.mvc.perform(post("/v1/own/events/" + this.slug + "/cover/remove").with(person("u1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.coverImageUrl").doesNotExist());
		assertThat(files()).isEmpty();
	}

	@Test
	void onlyWhoMayEditAndOnlyAModestWebp() throws Exception {
		upload("u2", webp("theirs")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_YOURS"));
		upload("u1", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)).andExpect(jsonPath("$.code").value("INVALID"));
		upload("u1", webp("x".repeat(CoverFiles.MAX_BYTES))).andExpect(jsonPath("$.code").value("INVALID"));
		assertThat(files()).isEmpty();
	}

	@Test
	void moderationTakesThePictureAloneDownAndTellsTheOwner() throws Exception {
		removeByModeration("Spam").andExpect(jsonPath("$.code").value("WRONG_STATE"));
		upload("u1", webp("one"));

		this.mvc.perform(post("/v1/moderation/events/" + this.slug + "/cover/remove").with(person("u2"))
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Spam\"}")).andExpect(status().isForbidden());
		removeByModeration("Erkennbare Personen ohne Einwilligung").andExpect(status().isNoContent());

		assertThat(files()).isEmpty();
		this.mvc.perform(get("/v1/events/" + this.slug).param("locale", "en"))
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.coverImageUrl").doesNotExist());
		assertThat(count("notice_outbox WHERE kind = 'EVENT_COVER_REMOVED' AND recipient_user_id = 'u1'")).isEqualTo(1);
		assertThat(count("audit_log WHERE action = 'COVER_REMOVED' AND operator = 'mod'")).isEqualTo(1);
	}

	/** A WebP header and some bytes: the service checks the form, the frontend has drawn the picture. */
	private static byte[] webp(String content) {
		byte[] body = (content + "-".repeat(32)).getBytes(StandardCharsets.US_ASCII);
		byte[] bytes = new byte[20 + body.length];
		System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
		System.arraycopy("WEBPVP8L".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 8);
		System.arraycopy(body, 0, bytes, 20, body.length);
		return bytes;
	}

	private ResultActions upload(String subject, byte[] bytes) throws Exception {
		return this.mvc.perform(put("/v1/own/events/" + this.slug + "/cover").with(person(subject))
			.contentType("image/webp")
			.content(bytes));
	}

	private ResultActions removeByModeration(String reason) throws Exception {
		return this.mvc.perform(post("/v1/moderation/events/" + this.slug + "/cover/remove").with(moderator("mod"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"" + reason + "\"}"));
	}

	private static List<Path> files() throws IOException {
		try (Stream<Path> files = Files.list(media)) {
			return files.filter((file) -> file.getFileName().toString().endsWith(".webp")).toList();
		}
	}

	private long count(String from) {
		return this.jdbc.sql("SELECT count(*) FROM " + from).query(Long.class).single();
	}

	private static String body(String name) {
		return """
				{"locale":"de","name":"%s","placeName":"Monschau, Deutschland",
				 "description":"Eine Runde durchs Hohe Venn, etwa 12 km.","activityType":"HIKING",
				 "difficulty":"EASY","startsAt":"%s","latitude":50.5547,"longitude":6.2425,
				 "eventLinks":["https://t.me/example_group"]}
				""".formatted(name, IN_A_WEEK);
	}

}
