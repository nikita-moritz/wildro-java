package app.wildro.community.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

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
import app.wildro.community.MutableClock;
import app.wildro.community.TestcontainersConfiguration;
import app.wildro.community.people.Person;

/** SPEC.md §18.3 and §18.4 through HTTP: who may do what in a community. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private FakePeople people;

	@Autowired
	private MutableClock clock;

	@Autowired
	private CommunityService service;

	@BeforeEach
	void clean() {
		this.jdbc.sql("TRUNCATE communities, audit_log, outbox, consumer_cursor RESTART IDENTITY CASCADE").update();
		this.people.reset();
	}

	@Test
	void theCreatorOwnsItAndThePublicPageShowsOnlyACount() throws Exception {
		create("owner", "Trailrunner Aachen").andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("trailrunner-aachen"))
			.andExpect(jsonPath("$.memberCount").value(1));
		this.mvc.perform(get("/v1/communities/trailrunner-aachen"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Trailrunner Aachen"))
			.andExpect(jsonPath("$.members").doesNotExist());
		this.mvc.perform(get("/v1/communities")).andExpect(jsonPath("$.items[0].slug").value("trailrunner-aachen"));
		this.mvc.perform(get("/v1/communities/trailrunner-aachen/me").with(person("owner")))
			.andExpect(jsonPath("$.role").value("OWNER"));
	}

	@Test
	void creatingNeedsTheCurrentTerms() throws Exception {
		this.people.put(new Person("owner", "Ada", false));
		create("owner", "Group").andExpect(jsonPath("$.code").value("TERMS_REQUIRED"));
	}

	@Test
	void onePersonOwnsAtMostThree() throws Exception {
		for (int i = 0; i < 3; i++) {
			create("owner", "Group " + i).andExpect(status().isCreated());
		}
		create("owner", "Group 3").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIMIT_OWNED"));
	}

	@Test
	void namesAreForMembersOnlyAndTheOwnerCannotSimplyLeave() throws Exception {
		create("owner", "Group");
		this.mvc.perform(get("/v1/communities/group/members").with(person("stranger"))).andExpect(status().isForbidden());
		this.mvc.perform(post("/v1/communities/group/join").with(person("ada"))).andExpect(jsonPath("$.role").value("MEMBER"));
		this.mvc.perform(get("/v1/communities/group/members").with(person("ada")))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].role").value("OWNER"));
		this.mvc.perform(post("/v1/communities/group/leave").with(person("owner")))
			.andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));
		this.mvc.perform(post("/v1/communities/group/leave").with(person("ada"))).andExpect(status().isOk());
	}

	@Test
	void anAdminRemovesMembersButNotOtherAdminsAndOnlyTheOwnerChangesRoles() throws Exception {
		create("owner", "Group");
		join("ada");
		join("bob");
		join("cid");
		role("ada", "ADMIN", "owner").andExpect(status().isNoContent());
		role("bob", "ADMIN", "ada").andExpect(status().isForbidden());
		this.mvc.perform(post("/v1/communities/group/members/cid/remove").with(person("ada")))
			.andExpect(status().isNoContent());
		role("bob", "ADMIN", "owner");
		this.mvc.perform(post("/v1/communities/group/members/bob/remove").with(person("ada")))
			.andExpect(status().isForbidden());
		this.mvc.perform(post("/v1/communities/group/members/owner/remove").with(person("owner")))
			.andExpect(status().isForbidden());
	}

	@Test
	void ownershipGoesOnlyToAnAdminAndIsAudited() throws Exception {
		create("owner", "Group");
		join("ada");
		transfer("ada").andExpect(jsonPath("$.code").value("TARGET_NOT_ADMIN"));
		role("ada", "ADMIN", "owner");
		transfer("ada").andExpect(status().isNoContent());
		this.mvc.perform(get("/v1/communities/group/me").with(person("ada"))).andExpect(jsonPath("$.role").value("OWNER"));
		this.mvc.perform(get("/v1/communities/group/me").with(person("owner"))).andExpect(jsonPath("$.role").value("ADMIN"));
		assertThat(count("audit_log WHERE action = 'OWNERSHIP_TRANSFERRED'")).isEqualTo(1);
	}

	@Test
	void closingKeepsThePageAndTellsTheEventService() throws Exception {
		create("owner", "Group");
		this.mvc.perform(post("/v1/communities/group/close").with(person("owner"))).andExpect(status().isNoContent());
		this.mvc.perform(get("/v1/communities/group")).andExpect(jsonPath("$.status").value("CLOSED"));
		assertThat(count("outbox WHERE type = 'community.closed'")).isEqualTo(1);
		join("ada").andExpect(jsonPath("$.code").value("WRONG_STATE"));
	}

	@Test
	void whenTheOwnerIsErasedTheLongestServingAdminInheritsOtherwiseItCloses() throws Exception {
		create("owner", "Group");
		join("ada");
		join("bob");
		role("bob", "ADMIN", "owner");
		this.clock.advance(Duration.ofDays(1));
		role("ada", "ADMIN", "owner");
		create("owner", "Lonely");

		this.service.erase("owner");

		this.mvc.perform(get("/v1/communities/group/me").with(person("bob"))).andExpect(jsonPath("$.role").value("OWNER"));
		this.mvc.perform(get("/v1/communities/lonely")).andExpect(jsonPath("$.status").value("CLOSED"));
		assertThat(count("memberships WHERE user_id = 'owner'")).isZero();
		assertThat(count("outbox WHERE type = 'community.closed'")).isEqualTo(1);
	}

	@Test
	void aModeratorTakesACommunityOffTheSiteAndPutsItBackWithAnAuditTrail() throws Exception {
		create("owner", "Group");
		suspend("Spam", person("ada")).andExpect(status().isForbidden());
		suspend(" ", person("mod", "moderator")).andExpect(status().isBadRequest());
		suspend("Spam", person("mod", "moderator")).andExpect(status().isNoContent());
		this.mvc.perform(get("/v1/communities/group")).andExpect(status().isNotFound());
		join("ada").andExpect(status().isNotFound());
		suspend("Again", person("mod", "moderator")).andExpect(jsonPath("$.code").value("WRONG_STATE"));

		this.mvc.perform(post("/v1/moderation/communities/group/reinstate").with(person("mod", "moderator")))
			.andExpect(status().isNoContent());
		this.mvc.perform(get("/v1/communities/group")).andExpect(jsonPath("$.status").value("ACTIVE"));
		assertThat(count("audit_log WHERE action IN ('COMMUNITY_SUSPENDED', 'COMMUNITY_REINSTATED') AND actor = 'mod'"))
			.isEqualTo(2);
	}

	@Test
	void aSuspensionQueuesTheOwnersStatementOfReasonsInTheSameTransaction() throws Exception {
		create("owner", "Group");
		suspend("Werbung statt Wandern", person("mod", "moderator")).andExpect(status().isNoContent());

		var payload = this.jdbc.sql("""
				SELECT concat_ws('|', payload->>'ownerUserId', payload->>'name', payload->>'locale', payload->>'reason')
				FROM outbox WHERE type = 'community.suspended'
				""").query(String.class).single();
		assertThat(payload).isEqualTo("owner|Group|de|Werbung statt Wandern");
		// Refused, nothing written: no second notice for a second attempt.
		suspend("Again", person("mod", "moderator")).andExpect(status().isConflict());
		assertThat(count("outbox WHERE type = 'community.suspended'")).isEqualTo(1);
	}

	@Test
	void theListPutsSuspendedFirstAndShowsNamesOnlyToTheSuperuser() throws Exception {
		create("owner", "Group");
		join("ada");
		role("ada", "ADMIN", "owner");
		create("other", "Wandertreff");
		suspend("Spam", person("root", "superuser"));

		this.mvc.perform(get("/v1/moderation/communities").with(person("ada"))).andExpect(status().isForbidden());
		this.mvc.perform(get("/v1/moderation/communities").with(person("mod", "moderator")))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].slug").value("group"))
			.andExpect(jsonPath("$[0].status").value("SUSPENDED"))
			.andExpect(jsonPath("$[0].suspensionReason").value("Spam"))
			.andExpect(jsonPath("$[0].memberCount").value(2))
			.andExpect(jsonPath("$[0].leaders.length()").value(0));
		this.mvc.perform(get("/v1/moderation/communities").param("q", "WANDER").with(person("mod", "moderator")))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].slug").value("wandertreff"));
		this.mvc.perform(get("/v1/moderation/communities").with(person("root", "superuser")))
			.andExpect(jsonPath("$[0].leaders.length()").value(2))
			.andExpect(jsonPath("$[0].leaders[0].role").value("OWNER"))
			.andExpect(jsonPath("$[0].leaders[1].userId").value("ada"));
	}

	@Test
	void onlyTheSuperuserHandsOverOwnershipAndOnlyToAnAdmin() throws Exception {
		create("owner", "Group");
		join("ada");
		moderationTransfer("ada", person("root", "superuser")).andExpect(jsonPath("$.code").value("TARGET_NOT_ADMIN"));
		role("ada", "ADMIN", "owner");
		moderationTransfer("ada", person("mod", "moderator")).andExpect(status().isForbidden());
		moderationTransfer("ada", person("root", "superuser")).andExpect(status().isNoContent());
		this.mvc.perform(get("/v1/communities/group/me").with(person("ada"))).andExpect(jsonPath("$.role").value("OWNER"));
		this.mvc.perform(get("/v1/communities/group/me").with(person("owner"))).andExpect(jsonPath("$.role").value("ADMIN"));
		assertThat(count("audit_log WHERE action = 'OWNERSHIP_TRANSFERRED' AND actor = 'root'")).isEqualTo(1);
	}

	@Test
	void linksAreHttpsOnly() throws Exception {
		this.mvc.perform(post("/v1/communities").with(person("owner"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"Group","description":"We hike.","area":"Aachen","locale":"de","links":["javascript:alert(1)"]}
					""")).andExpect(status().isBadRequest());
	}

	private ResultActions create(String subject, String name) throws Exception {
		return this.mvc.perform(post("/v1/communities").with(person(subject))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"%s","description":"Wir wandern sonntags.","area":"Aachen","locale":"de",
					 "links":["https://t.me/example_group"]}
					""".formatted(name)));
	}

	private ResultActions join(String subject) throws Exception {
		return this.mvc.perform(post("/v1/communities/group/join").with(person(subject)));
	}

	private ResultActions role(String userId, String role, String actor) throws Exception {
		return this.mvc.perform(post("/v1/communities/group/members/" + userId + "/role").with(person(actor))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"" + role + "\"}"));
	}

	private ResultActions transfer(String to) throws Exception {
		return this.mvc.perform(post("/v1/communities/group/transfer").with(person("owner"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":\"" + to + "\"}"));
	}

	private ResultActions suspend(String reason, JwtRequestPostProcessor as) throws Exception {
		return this.mvc.perform(post("/v1/moderation/communities/group/suspend").with(as)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"" + reason + "\"}"));
	}

	private ResultActions moderationTransfer(String to, JwtRequestPostProcessor as) throws Exception {
		return this.mvc.perform(post("/v1/moderation/communities/group/transfer").with(as)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":\"" + to + "\"}"));
	}

	private long count(String from) {
		return this.jdbc.sql("SELECT count(*) FROM " + from).query(Long.class).single();
	}

	static JwtRequestPostProcessor person(String subject, String... roles) {
		List<String> all = roles.length == 0 ? List.of("user") : List.of(roles);
		return jwt().jwt((token) -> token.subject(subject).claim("roles", all))
			.authorities(all.stream().<GrantedAuthority>map((r) -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
	}

}
