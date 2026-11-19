package app.wildro.event.own;

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
import java.util.List;

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
import app.wildro.event.internal.OutboxConsumers;

/** Events of communities (SPEC.md §18.3) and what other modules decide about events (§18.4). */
@Import({ TestcontainersConfiguration.class, OwnTestConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommunityEventsTest {

	private static final Instant IN_A_WEEK = Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.HOURS);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private FakeCommunities communities;

	@Autowired
	private FakeWriters writers;

	@Autowired
	private OutboxConsumers consumers;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private app.wildro.event.internal.RecordingInvalidator cache;

	@BeforeEach
	void reset() {
		this.communities.reset();
		this.writers.reset();
		this.cache.reset();
		this.communities.community(7, "trailrunner-aachen", "Trailrunner Aachen", "ACTIVE");
		this.communities.role("trailrunner-aachen", "admin", "ADMIN");
		this.communities.role("trailrunner-aachen", "other-admin", "ADMIN");
		this.communities.role("trailrunner-aachen", "member", "MEMBER");
	}

	@Test
	void anAdminWritesInTheCommunitysNameAndTheCommunityShowsAsOrganiser() throws Exception {
		String slug = slugOf(create("admin", "Morning run", "trailrunner-aachen").andExpect(status().isCreated()));
		this.mvc.perform(get("/v1/events/" + slug).param("locale", "de"))
			.andExpect(jsonPath("$.organizer").value("Trailrunner Aachen"))
			.andExpect(jsonPath("$.communitySlug").value("trailrunner-aachen"));
		this.mvc.perform(get("/v1/events/community/7").param("locale", "de"))
			.andExpect(jsonPath("$[0].slug").value(slug));
	}

	@Test
	void aMemberIsNotAnAdmin() throws Exception {
		create("member", "Morning run", "trailrunner-aachen").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_YOURS"));
	}

	@Test
	void anotherAdminOfTheCommunityMayEditButNotAStranger() throws Exception {
		String slug = slugOf(create("admin", "Morning run", "trailrunner-aachen"));
		edit(slug, "stranger").andExpect(status().isForbidden());
		edit(slug, "other-admin").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"));
		// Yesterday's admin is not today's: the role is asked on every write.
		this.communities.role("trailrunner-aachen", "other-admin", "MEMBER");
		edit(slug, "other-admin").andExpect(status().isForbidden());
	}

	@Test
	void aClosedCommunitysFutureEventsAreCancelled() throws Exception {
		String slug = slugOf(create("admin", "Morning run", "trailrunner-aachen"));
		this.consumers.consume("community-service",
				List.of(new OutboxConsumers.Entry(1, "community.closed", "7", "{}", Instant.now().toString())));
		this.mvc.perform(get("/v1/events/" + slug).param("locale", "de")).andExpect(jsonPath("$.status").value("CANCELLED"));
		// The cached feed and the event's page are dropped, not left to expire.
		assertThat(this.cache.tags()).containsExactlyInAnyOrder("events:feed", "event:" + slug);
		assertThat(this.jdbc.sql("SELECT last_id FROM consumer_cursor WHERE source = 'community-service'")
			.query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void anErasedPersonsFutureEventsAreCancelledAndTheirNameLeaves() throws Exception {
		String personal = slugOf(create("ada", "Personal hike", null));
		String forCommunity = slugOf(create("admin", "Community hike", "trailrunner-aachen"));
		this.consumers.consume("user-service",
				List.of(new OutboxConsumers.Entry(5, "user.erased", "ada", "{}", Instant.now().toString()),
						new OutboxConsumers.Entry(6, "user.erased", "admin", "{}", Instant.now().toString())));
		this.mvc.perform(get("/v1/events/" + personal).param("locale", "de"))
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.organizer").doesNotExist());
		// The community's event stays: it belongs to the community, not to the author.
		this.mvc.perform(get("/v1/events/" + forCommunity).param("locale", "de"))
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.organizer").value("Trailrunner Aachen"));
	}

	private ResultActions create(String subject, String name, String community) throws Exception {
		return this.mvc.perform(post("/v1/own/events").with(person(subject))
			.param("publish", "true")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(name, community)));
	}

	private ResultActions edit(String slug, String subject) throws Exception {
		return this.mvc.perform(put("/v1/own/events/" + slug).with(person(subject))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body("Renamed", "trailrunner-aachen")));
	}

	private static String slugOf(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString().replaceAll(".*\"slug\":\"([^\"]+)\".*", "$1");
	}

	private static String body(String name, String community) {
		return """
				{"locale":"de","name":"%s","placeName":"Aachen","description":"Wir laufen.","activityType":"TRAIL_RUN",
				 "startsAt":"%s","latitude":50.77,"longitude":6.08%s}
				""".formatted(name, IN_A_WEEK, community == null ? "" : ",\"communitySlug\":\"" + community + "\"");
	}

}
