package app.wildro.analytics.track;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import app.wildro.analytics.MutableClock;
import app.wildro.analytics.TestcontainersConfiguration;

/**
 * Which views count, through HTTP, against a real Postgres. Each test is one
 * rule of SPEC.md §13.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "analytics.salt=test-salt")
@AutoConfigureMockMvc
class TrackControllerTest {

	private static final String BROWSER = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/141 Safari/537.36";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void clean() {
		this.jdbc.sql("DELETE FROM page_view").update();
		// The rate limiter keeps its window between tests that share a context;
		// a fresh minute for every test keeps them independent.
		this.clock.advance(Duration.ofMinutes(5));
	}

	@Test
	void aViewOnTheSiteHostIsStoredWithLanguageAndAHash() throws Exception {
		track(view("/de/event/eifel-tour", "wildro.app", "203.0.113.7", BROWSER));

		Map<String, Object> row = this.jdbc.sql("SELECT * FROM page_view").query().singleRow();
		assertThat(row.get("path")).isEqualTo("/de/event/eifel-tour");
		assertThat(row.get("locale")).isEqualTo("de");
		assertThat(row.get("host")).isEqualTo("wildro.app");
		assertThat((String) row.get("visitor_hash")).matches("[0-9a-f]{32}");
	}

	@Test
	void theAddressAndTheUserAgentAreNotStored() throws Exception {
		track(view("/de", "wildro.app", "203.0.113.7", BROWSER));

		String row = this.jdbc.sql("SELECT page_view::text FROM page_view").query(String.class).single();
		assertThat(row).doesNotContain("203.0.113.7").doesNotContain("Chrome");
	}

	@Test
	void otherHostsAreAcceptedAndDropped() throws Exception {
		track(view("/de", "wildro.nikone.dev", "203.0.113.7", BROWSER));
		track(view("/de", "wildro-git-dev.vercel.app", "203.0.113.7", BROWSER));
		track(view("/de", "localhost", "203.0.113.7", BROWSER));

		assertThat(rows()).isZero();
	}

	@Test
	void theSiteHostCountsWithAPortAndInAnyCase() throws Exception {
		track(view("/en", "WILDRO.APP:443", "203.0.113.7", BROWSER));

		assertThat(rows()).isEqualTo(1);
	}

	@Test
	void botsAndMissingUserAgentsAreDropped() throws Exception {
		track(view("/de", "wildro.app", "203.0.113.7", "Mozilla/5.0 (compatible; Googlebot/2.1)"));
		track(view("/de", "wildro.app", "203.0.113.7", "Mozilla/5.0 HeadlessChrome/141"));
		track(view("/de", "wildro.app", "203.0.113.7", null));

		assertThat(rows()).isZero();
	}

	@Test
	void garbageIsAcceptedAndDropped() throws Exception {
		this.mvc.perform(post("/v1/track").contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isAccepted());
		this.mvc.perform(post("/v1/track").contentType(MediaType.TEXT_PLAIN).content("hello"))
			.andExpect(status().isAccepted());
		this.mvc.perform(post("/v1/track").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isAccepted());
		track(view("no-leading-slash", "wildro.app", "203.0.113.7", BROWSER));

		assertThat(rows()).isZero();
	}

	@Test
	void sixtyViewsPerAddressAndMinuteCountTheRestDoNot() throws Exception {
		for (int i = 0; i < 61; i++) {
			track(view("/de", "wildro.app", "198.51.100.1", BROWSER));
		}
		assertThat(rows()).isEqualTo(60);

		track(view("/de", "wildro.app", "198.51.100.2", BROWSER));
		assertThat(rows()).isEqualTo(61);

		this.clock.advance(Duration.ofMinutes(1));
		track(view("/de", "wildro.app", "198.51.100.1", BROWSER));
		assertThat(rows()).isEqualTo(62);
	}

	@Test
	void anUnknownFirstSegmentHasNoLanguage() throws Exception {
		track(view("/robots.txt", "wildro.app", "203.0.113.7", BROWSER));

		assertThat(this.jdbc.sql("SELECT locale FROM page_view").query().singleRow().get("locale")).isNull();
	}

	private long rows() {
		return this.jdbc.sql("SELECT count(*) FROM page_view").query(Long.class).single();
	}

	private void track(String body) throws Exception {
		this.mvc.perform(post("/v1/track").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isAccepted());
	}

	private static String view(String path, String host, String ip, String userAgent) {
		return """
				{"path":%s,"referrer":null,"host":%s,"clientIp":%s,"userAgent":%s}
				""".formatted(json(path), json(host), json(ip), json(userAgent));
	}

	private static String json(String value) {
		return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\"";
	}

}
