package app.wildro.user.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import app.wildro.user.FakeDirectory;
import app.wildro.user.TestcontainersConfiguration;
import app.wildro.user.identity.Identity;

/**
 * The account endpoints. Tokens are built by Spring Security's test support,
 * which stands in for the elvix JWT after the signature and audience checks;
 * those checks belong to the resource-server library and are configured, not
 * written, here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private FakeDirectory directory;

	@BeforeEach
	void clean() {
		this.jdbc.sql("DELETE FROM users").update();
		this.directory.reset();
		this.directory.put(new Identity("u1", "ada@example.org", "Ada Lovelace"));
	}

	@Test
	void withoutATokenNothingIsAnswered() throws Exception {
		this.mvc.perform(get("/v1/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void theFirstVisitFillsTheAccountFromElvixAndLaterOnesDoNotAskAgain() throws Exception {
		this.mvc.perform(get("/v1/me").with(person("u1", "user", "moderator")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("u1"))
			.andExpect(jsonPath("$.email").value("ada@example.org"))
			.andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
			.andExpect(jsonPath("$.roles[0]").value("moderator"))
			.andExpect(jsonPath("$.roles[1]").value("user"))
			.andExpect(jsonPath("$.terms.current").value("2026-11-18"))
			.andExpect(jsonPath("$.terms.upToDate").value(false));

		this.mvc.perform(get("/v1/me").with(person("u1", "user"))).andExpect(status().isOk());
		assertThat(this.directory.calls()).isEqualTo(1);
	}

	@Test
	void aNameElvixHoldsThatBreaksOurRuleIsNotCarriedOver() throws Exception {
		this.directory.put(new Identity("u2", "x@example.org", "X"));
		this.mvc.perform(get("/v1/me").with(person("u2", "user")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").doesNotExist());
	}

	@Test
	void aPersonElvixDoesNotKnowIsRefused() throws Exception {
		this.mvc.perform(get("/v1/me").with(person("ghost", "user"))).andExpect(status().isUnauthorized());
	}

	@Test
	void whenElvixIsDownTheFirstVisitSaysSoInsteadOfInventingAnAccount() throws Exception {
		this.directory.down(true);
		this.mvc.perform(get("/v1/me").with(person("u1", "user"))).andExpect(status().isServiceUnavailable());
		assertThat(this.jdbc.sql("SELECT count(*) FROM users").query(Integer.class).single()).isZero();
	}

	@Test
	void theNameIsTrimmedAndCheckedAgainstTheRule() throws Exception {
		this.mvc.perform(put("/v1/me/display-name").with(person("u1", "user"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"displayName\": \"  Ada   L.  \"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Ada L."));

		this.mvc.perform(put("/v1/me/display-name").with(person("u1", "user"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"displayName\": \"\\uD83C\\uDFD4\"}"))
			.andExpect(status().isUnprocessableContent());
		this.mvc.perform(put("/v1/me/display-name").with(person("u1", "user"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"displayName\": \"" + "a".repeat(51) + "\"}"))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void termsAreAcceptedOnlyInTheVersionThatWasShown() throws Exception {
		this.mvc.perform(post("/v1/me/terms").with(person("u1", "user"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\": \"2026-01-01\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.currentVersion").value("2026-11-18"));

		this.mvc.perform(post("/v1/me/terms").with(person("u1", "user"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\": \"2026-11-18\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.terms.accepted").value("2026-11-18"))
			.andExpect(jsonPath("$.terms.acceptedAt").value("2026-11-18T10:00:00Z"))
			.andExpect(jsonPath("$.terms.upToDate").value(true));
	}

	@Test
	void noRouteReachesAnotherPersonOrAnythingUnlisted() throws Exception {
		this.mvc.perform(get("/v1/users/u1").with(person("u1", "superuser"))).andExpect(status().isForbidden());
	}

	/** A verified elvix token for this subject, with the authorities the roles claim maps to. */
	static JwtRequestPostProcessor person(String subject, String... roles) {
		return jwt().jwt((token) -> token.subject(subject).claim("roles", java.util.List.of(roles)))
			.authorities(java.util.Arrays.stream(roles)
				.<GrantedAuthority>map((role) -> new SimpleGrantedAuthority("ROLE_" + role))
				.toList());
	}

}
