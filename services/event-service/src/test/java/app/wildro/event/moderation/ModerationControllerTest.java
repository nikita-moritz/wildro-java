package app.wildro.event.moderation;

import static app.wildro.event.own.People.moderator;
import static app.wildro.event.own.People.person;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import app.wildro.event.TestcontainersConfiguration;
import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.EventTranslation;
import app.wildro.event.domain.GeoPoints;
import app.wildro.event.own.OwnTestConfiguration;

/** Notice and action, SPEC.md §18.2: reports in, decisions out, the owner told why. */
@Import({ TestcontainersConfiguration.class, OwnTestConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ModerationControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private EventRepository events;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private ReportRateLimiter limiter;

	@BeforeEach
	void reset() {
		this.limiter.reset();
	}

	@Test
	void aGuestMayReportFiveTimesAnHourFromOneAddress() throws Exception {
		Event event = ownEvent("one", EventStatus.PUBLISHED);
		for (int i = 0; i < 5; i++) {
			report(event.getSlug(), "203.0.113.7").andExpect(status().isAccepted());
		}
		report(event.getSlug(), "203.0.113.7").andExpect(status().isTooManyRequests());
		report(event.getSlug(), "203.0.113.8").andExpect(status().isAccepted());
	}

	@Test
	void whatThePublicCannotSeeCannotBeReported() throws Exception {
		report(ownEvent("draft", EventStatus.DRAFT).getSlug(), "203.0.113.7").andExpect(status().isNotFound());
	}

	@Test
	void theQueueIsForModeratorsOnly() throws Exception {
		this.mvc.perform(get("/v1/moderation/reports").with(person("u1"))).andExpect(status().isForbidden());
		this.mvc.perform(get("/v1/moderation/reports")).andExpect(status().isUnauthorized());
	}

	@Test
	void takingDownHidesThePageClosesTheReportsAndQueuesTheOwnersNotice() throws Exception {
		Event event = ownEvent("bad", EventStatus.PUBLISHED);
		report(event.getSlug(), "203.0.113.7");
		this.mvc.perform(get("/v1/moderation/reports").with(moderator("m1")))
			.andExpect(jsonPath("$[0].slug").value(event.getSlug()))
			.andExpect(jsonPath("$[0].reason").value("SPAM"));

		this.mvc.perform(post("/v1/moderation/events/" + event.getSlug() + "/suspend").with(moderator("m1"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Werbung für ein kommerzielles Angebot, keine Tour.\"}"))
			.andExpect(status().isNoContent());

		this.mvc.perform(get("/v1/events/" + event.getSlug()).param("locale", "de")).andExpect(status().isNotFound());
		assertThat(this.jdbc.sql("SELECT status FROM reports").query(String.class).single()).isEqualTo("ACTIONED");
		assertThat(this.jdbc.sql("SELECT recipient_user_id FROM notice_outbox WHERE kind = 'EVENT_SUSPENDED'")
			.query(String.class)
			.single()).isEqualTo("owner-1");
		this.mvc.perform(get("/v1/own/events/" + event.getSlug()).with(person("owner-1")))
			.andExpect(jsonPath("$.status").value("SUSPENDED"))
			.andExpect(jsonPath("$.suspensionReason").value("Werbung für ein kommerzielles Angebot, keine Tour."));

		this.mvc.perform(post("/v1/moderation/events/" + event.getSlug() + "/reinstate").with(moderator("m1")))
			.andExpect(status().isNoContent());
		this.mvc.perform(get("/v1/events/" + event.getSlug()).param("locale", "de")).andExpect(status().isOk());
	}

	@Test
	void aDismissedReportLeavesTheEventAndATraceInTheAuditLog() throws Exception {
		Event event = ownEvent("fine", EventStatus.PUBLISHED);
		report(event.getSlug(), "203.0.113.7");
		long id = this.jdbc.sql("SELECT id FROM reports").query(Long.class).single();
		this.mvc.perform(post("/v1/moderation/reports/" + id + "/dismiss").with(moderator("m1")))
			.andExpect(status().isNoContent());
		assertThat(this.jdbc.sql("SELECT status FROM reports").query(String.class).single()).isEqualTo("DISMISSED");
		assertThat(this.jdbc.sql("SELECT count(*) FROM audit_log WHERE action = 'REPORT_DISMISSED'")
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	private ResultActions report(String slug, String address) throws Exception {
		return this.mvc.perform(post("/v1/reports").header("X-Client-Address", address)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"slug\":\"" + slug + "\",\"reason\":\"SPAM\",\"details\":\"Reine Werbung.\"}"));
	}

	private Event ownEvent(String slug, EventStatus status) {
		EventTranslation text = new EventTranslation("Tour " + slug, "Aachen", "Beschreibung.");
		return this.events.saveAndFlush(Event.builder()
			.slug(slug)
			.ownedBy("owner-1", "de")
			.status(status)
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

}
