package app.wildro.analytics.stats;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import app.wildro.analytics.MutableClock;
import app.wildro.analytics.TestcontainersConfiguration;

/** The aggregates, in the exact shape the nikone.dev dashboard parses. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "analytics.salt=test-salt")
@AutoConfigureMockMvc
class StatsControllerTest {

	private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void clean() {
		this.jdbc.sql("DELETE FROM page_view").update();
		this.clock.set(NOW);
	}

	@Test
	void sessionsAreCutAtThirtyMinutesOfSilence() throws Exception {
		String a = "a".repeat(32);
		String b = "b".repeat(32);
		// a: two views four minutes apart, then a third after an hour: two sessions.
		insert("/de", a, null, null, NOW.minus(Duration.ofMinutes(70)));
		insert("/de/map", a, null, null, NOW.minus(Duration.ofMinutes(66)));
		insert("/de", a, null, null, NOW.minus(Duration.ofMinutes(5)));
		// b: one view, one session, a bounce.
		insert("/en", b, null, null, NOW.minus(Duration.ofMinutes(10)));

		this.mvc.perform(get("/v1/stats").param("period", "day"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.project").value("wildro"))
			.andExpect(jsonPath("$.period").value("day"))
			.andExpect(jsonPath("$.traffic.views").value(4))
			.andExpect(jsonPath("$.traffic.totalViews").value(4))
			.andExpect(jsonPath("$.traffic.uniques").value(2))
			.andExpect(jsonPath("$.traffic.sessions").value(3))
			// sessions of 2, 1 and 1 views: two of three are bounces
			.andExpect(jsonPath("$.traffic.bounceRate").value(closeTo(2.0 / 3, 1e-9)))
			// durations 240 s, 0 s and 0 s
			.andExpect(jsonPath("$.traffic.avgDurationSec").value(closeTo(80.0, 1e-6)))
			.andExpect(jsonPath("$.topPages[0].name").value("/de"))
			.andExpect(jsonPath("$.topPages[0].count").value(2))
			.andExpect(jsonPath("$.business[0].key").value("locale_de"))
			.andExpect(jsonPath("$.business[0].value").value(3));
	}

	@Test
	void sourcesPreferTheCampaignThenTheReferrerAndTreatTheSiteItselfAsDirect() throws Exception {
		insertRef("/de", "https://www.instagram.com/p/x", null);
		insertRef("/de", "https://www.instagram.com/", null);
		insertRef("/de", "https://wildro.app/de/map", null);
		insertRef("/de", null, null);
		insertRef("/de", "https://t.me/channel", "newsletter");

		this.mvc.perform(get("/v1/stats").param("period", "week"))
			.andExpect(jsonPath("$.topSources[0].name").value("direct"))
			.andExpect(jsonPath("$.topSources[0].count").value(2))
			.andExpect(jsonPath("$.topSources[1].name").value("www.instagram.com"))
			.andExpect(jsonPath("$.topSources[1].count").value(2))
			.andExpect(jsonPath("$.topSources[2].name").value("newsletter"));
	}

	@Test
	void withoutVisitorHashesTheVisitorMetricsAreNullNotZero() throws Exception {
		insert("/de", null, null, null, NOW.minus(Duration.ofMinutes(3)));

		this.mvc.perform(get("/v1/stats").param("period", "month"))
			.andExpect(jsonPath("$.traffic.views").value(1))
			.andExpect(jsonPath("$.traffic", hasKey("uniques")))
			.andExpect(jsonPath("$.traffic.uniques").value(nullValue()))
			.andExpect(jsonPath("$.traffic.sessions").value(nullValue()))
			.andExpect(jsonPath("$.traffic.bounceRate").value(nullValue()))
			.andExpect(jsonPath("$.traffic.avgDurationSec").value(nullValue()))
			.andExpect(jsonPath("$.traffic.series[0].uniques").value(nullValue()));
	}

	@Test
	void thePeriodBoundsTheViewsButNotTheTotal() throws Exception {
		insert("/de", "c".repeat(32), null, null, NOW.minus(Duration.ofDays(3)));
		insert("/de", "c".repeat(32), null, null, NOW.minus(Duration.ofHours(1)));

		this.mvc.perform(get("/v1/stats").param("period", "day"))
			.andExpect(jsonPath("$.traffic.views").value(1))
			.andExpect(jsonPath("$.traffic.totalViews").value(2));
		this.mvc.perform(get("/v1/stats").param("period", "all"))
			.andExpect(jsonPath("$.period").value("all"))
			.andExpect(jsonPath("$.traffic.views").value(2));
	}

	@Test
	void anUnknownPeriodIsAMonth() throws Exception {
		this.mvc.perform(get("/v1/stats").param("period", "decade"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.period").value("month"))
			.andExpect(jsonPath("$.traffic.views").value(0));
	}

	private void insertRef(String path, String referrer, String utmSource) {
		insert(path, "d".repeat(32), referrer, utmSource, NOW.minus(Duration.ofHours(2)));
	}

	private void insert(String path, String hash, String referrer, String utmSource, Instant at) {
		this.jdbc.sql("""
				INSERT INTO page_view (viewed_at, path, locale, host, referrer, utm_source, visitor_hash)
				VALUES (:at, :path, :locale, 'wildro.app', :referrer, :utm, :hash)""")
			.param("at", Timestamp.from(at))
			.param("path", path)
			.param("locale", path.substring(1, 3))
			.param("referrer", referrer)
			.param("utm", utmSource)
			.param("hash", hash)
			.update();
	}

}
