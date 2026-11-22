package app.wildro.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import app.wildro.community.FakePeople;
import app.wildro.community.TestcontainersConfiguration;

/** Reports on communities (DSA Art. 16) and what moderation does with them. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityReportsTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private FakePeople people;

	@Autowired
	private ReportRateLimiter limiter;

	@BeforeEach
	void clean() {
		this.jdbc.sql("TRUNCATE communities, audit_log, outbox, consumer_cursor RESTART IDENTITY CASCADE").update();
		this.people.reset();
		this.limiter.reset();
	}

	@Test
	void aGuestReportsAndTheGroupComesFirstInModeration() throws Exception {
		create("owner", "Group");
		create("other", "Newer group");
		report("group", "SPAM", "Werbung statt Wandern").andExpect(status().isAccepted());

		this.mvc.perform(get("/v1/moderation/communities").with(person("mod", "moderator")))
			.andExpect(jsonPath("$[0].slug").value("group"))
			.andExpect(jsonPath("$[0].reports.length()").value(1))
			.andExpect(jsonPath("$[0].reports[0].reason").value("SPAM"))
			.andExpect(jsonPath("$[0].reports[0].details").value("Werbung statt Wandern"))
			.andExpect(jsonPath("$[1].reports.length()").value(0));
	}

	@Test
	void anUnknownReasonOrAMissingTextIsRefusedAndOnlyActiveGroupsCanBeReported() throws Exception {
		create("owner", "Group");
		report("group", "BORING", "x").andExpect(status().isBadRequest());
		report("group", "SPAM", " ").andExpect(status().isBadRequest());
		report("nowhere", "SPAM", "x").andExpect(status().isNotFound());
		this.mvc.perform(post("/v1/communities/group/close").with(person("owner"))).andExpect(status().isNoContent());
		report("group", "SPAM", "x").andExpect(status().isConflict());
	}

	@Test
	void fiveReportsAnHourPerAddress() throws Exception {
		create("owner", "Group");
		for (int i = 0; i < 5; i++) {
			report("group", "SPAM", "again " + i).andExpect(status().isAccepted());
		}
		report("group", "SPAM", "one too many").andExpect(status().isTooManyRequests());
		assertThat(count("community_reports")).isEqualTo(5);
	}

	@Test
	void dismissingClosesTheReportAndIsAudited() throws Exception {
		create("owner", "Group");
		report("group", "OTHER", "Unklar");
		long id = this.jdbc.sql("SELECT id FROM community_reports").query(Long.class).single();

		this.mvc.perform(post("/v1/moderation/reports/" + id + "/dismiss").with(person("ada")))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/v1/moderation/reports/" + id + "/dismiss").with(person("mod", "moderator")))
			.andExpect(status().isNoContent());
		this.mvc.perform(post("/v1/moderation/reports/" + id + "/dismiss").with(person("mod", "moderator")))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		assertThat(count("community_reports WHERE status = 'DISMISSED' AND resolved_by = 'mod'")).isEqualTo(1);
		assertThat(count("audit_log WHERE action = 'REPORT_DISMISSED' AND actor = 'mod'")).isEqualTo(1);
		this.mvc.perform(get("/v1/communities/group")).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void takingTheGroupDownClosesItsReports() throws Exception {
		create("owner", "Group");
		report("group", "SPAM", "eins");
		report("group", "ILLEGAL", "zwei");
		this.mvc.perform(post("/v1/moderation/communities/group/suspend").with(person("mod", "moderator"))
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Spam\"}")).andExpect(status().isNoContent());

		assertThat(count("community_reports WHERE status = 'ACTIONED' AND resolved_by = 'mod'")).isEqualTo(2);
		this.mvc.perform(get("/v1/moderation/communities").with(person("mod", "moderator")))
			.andExpect(jsonPath("$[0].reports.length()").value(0));
	}

	@Test
	void theReasonListMatchesTheSchema() throws Exception {
		String migration = Files.readString(Path.of("src/main/resources/db/migration/V3__community_reports.sql"));
		var matcher = Pattern.compile("community_reports_reason_known CHECK \\(reason IN \\(([^)]*)\\)").matcher(migration);
		assertThat(matcher.find()).isTrue();
		List<String> inSchema = Arrays.stream(matcher.group(1).split(","))
			.map((value) -> value.strip().replace("'", ""))
			.toList();
		assertThat(inSchema).containsExactly(Arrays.stream(ReportReason.values()).map(Enum::name).toArray(String[]::new));
	}

	private ResultActions create(String subject, String name) throws Exception {
		return this.mvc.perform(post("/v1/communities").with(person(subject))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"%s","description":"Wir wandern sonntags.","area":"Aachen","locale":"de","links":[]}
					""".formatted(name)));
	}

	private ResultActions report(String slug, String reason, String details) throws Exception {
		return this.mvc.perform(post("/v1/communities/" + slug + "/reports")
			.header("X-Client-Address", "203.0.113.7")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"" + reason + "\",\"details\":\"" + details + "\"}"));
	}

	private long count(String from) {
		return this.jdbc.sql("SELECT count(*) FROM " + from).query(Long.class).single();
	}

	private static JwtRequestPostProcessor person(String subject, String... roles) {
		List<String> all = roles.length == 0 ? List.of("user") : List.of(roles);
		return jwt().jwt((token) -> token.subject(subject).claim("roles", all))
			.authorities(all.stream().<GrantedAuthority>map((r) -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
	}

}
