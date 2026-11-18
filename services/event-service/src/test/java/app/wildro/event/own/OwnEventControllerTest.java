package app.wildro.event.own;

import static app.wildro.event.own.People.moderator;
import static app.wildro.event.own.People.person;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import app.wildro.event.TestcontainersConfiguration;

/** Own events through HTTP, SPEC.md §18.2: who may write, what limits hold, what the public sees. */
@Import({ TestcontainersConfiguration.class, OwnTestConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnEventControllerTest {

	private static final Instant IN_A_WEEK = Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.HOURS);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private FakeWriters writers;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void reset() {
		this.writers.reset();
	}

	@Test
	void aGuestWritesNothing() throws Exception {
		this.mvc.perform(post("/v1/own/events").contentType(MediaType.APPLICATION_JSON).content(body("Hike", IN_A_WEEK)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aDraftIsTheOwnersOnlyAndPublishingPutsItOnThePublicPage() throws Exception {
		create("u1", "Sonntagsrunde im Venn", IN_A_WEEK, false).andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("sonntagsrunde-im-venn-" + IN_A_WEEK.atZone(app.wildro.event.domain.Event.ZONE).toLocalDate()))
			.andExpect(jsonPath("$.status").value("DRAFT"));
		String slug = "sonntagsrunde-im-venn-" + IN_A_WEEK.atZone(app.wildro.event.domain.Event.ZONE).toLocalDate();

		this.mvc.perform(get("/v1/events/" + slug).param("locale", "en")).andExpect(status().isNotFound());
		this.mvc.perform(get("/v1/own/events/" + slug).with(person("u2"))).andExpect(status().isNotFound());

		this.mvc.perform(post("/v1/own/events/" + slug + "/publish").with(person("u1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		this.mvc.perform(get("/v1/events/" + slug).param("locale", "en"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("OWN"))
			.andExpect(jsonPath("$.originalLocale").value("de"))
			.andExpect(jsonPath("$.name").value("Sonntagsrunde im Venn"))
			.andExpect(jsonPath("$.organizer").value("Ada Lovelace"))
			.andExpect(jsonPath("$.sourceUrl").doesNotExist());
	}

	@Test
	void russianNamesBecomeLatinAddresses() throws Exception {
		create("u1", "Поход к озеру", IN_A_WEEK, false).andExpect(jsonPath("$.slug")
			.value("pokhod-k-ozeru-" + IN_A_WEEK.atZone(app.wildro.event.domain.Event.ZONE).toLocalDate()));
	}

	@Test
	void withoutTheCurrentTermsNothingIsWritten() throws Exception {
		this.writers.put(new Writer("u1", "Ada", false));
		create("u1", "Hike", IN_A_WEEK, true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TERMS_REQUIRED"));
		assertThat(count()).isZero();
	}

	@Test
	void anEventShowsItsOrganiserSoANameIsRequired() throws Exception {
		this.writers.put(new Writer("u1", null, true));
		create("u1", "Hike", IN_A_WEEK, true).andExpect(jsonPath("$.code").value("DISPLAY_NAME_REQUIRED"));
	}

	@Test
	void fivePublishedADayAndNoMore() throws Exception {
		for (int i = 0; i < 5; i++) {
			create("u1", "Hike " + i, IN_A_WEEK, true).andExpect(status().isCreated());
		}
		create("u1", "Hike 5", IN_A_WEEK, true).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LIMIT_PER_DAY"));
		// A draft is still allowed: the limit is on what goes public.
		create("u1", "Hike 6", IN_A_WEEK, false).andExpect(status().isCreated());
	}

	@Test
	void anEventInThePastIsNotCreated() throws Exception {
		create("u1", "Yesterday", Instant.now().minus(Duration.ofDays(1)), true)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("IN_THE_PAST"));
	}

	@Test
	void onlyTheOwnerOrAModeratorEditsAndTheModeratorIsInTheAuditLog() throws Exception {
		String slug = slugOf(create("u1", "Hike", IN_A_WEEK, true));
		this.mvc.perform(put("/v1/own/events/" + slug).with(person("u2"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body("Hike renamed", IN_A_WEEK))).andExpect(status().isForbidden());

		this.mvc.perform(put("/v1/own/events/" + slug).with(moderator("m1"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body("Hike renamed", IN_A_WEEK)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Hike renamed"));
		assertThat(this.jdbc.sql("SELECT operator FROM audit_log WHERE action = 'EVENT_UPDATED' AND entity_key = :slug")
			.param("slug", slug)
			.query(String.class)
			.single()).isEqualTo("m1");
	}

	@Test
	void whenTheAccountServiceIsDownNothingIsWritten() throws Exception {
		this.writers.down(true);
		create("u1", "Hike", IN_A_WEEK, true).andExpect(status().isServiceUnavailable());
		assertThat(count()).isZero();
	}

	@Test
	void theOwnerCancelsAndTheEventStaysAtItsAddress() throws Exception {
		String slug = slugOf(create("u1", "Hike", IN_A_WEEK, true));
		this.mvc.perform(post("/v1/own/events/" + slug + "/cancel").with(person("u1")))
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		this.mvc.perform(get("/v1/events/" + slug).param("locale", "de"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	private ResultActions create(String subject, String name, Instant startsAt, boolean publish) throws Exception {
		return this.mvc.perform(post("/v1/own/events").with(person(subject))
			.param("publish", String.valueOf(publish))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(name, startsAt)));
	}

	private static String slugOf(ResultActions result) throws Exception {
		String json = result.andReturn().getResponse().getContentAsString();
		return json.replaceAll(".*\"slug\":\"([^\"]+)\".*", "$1");
	}

	private long count() {
		return this.jdbc.sql("SELECT count(*) FROM events WHERE kind = 'OWN'").query(Long.class).single();
	}

	private static String body(String name, Instant startsAt) {
		return """
				{"locale":"de","name":"%s","placeName":"Monschau, Deutschland",
				 "description":"Eine Runde durchs Hohe Venn, etwa 12 km.","activityType":"HIKING",
				 "difficulty":"EASY","startsAt":"%s","latitude":50.5547,"longitude":6.2425,
				 "eventLinks":["https://t.me/example_group"]}
				""".formatted(name, startsAt);
	}

}
