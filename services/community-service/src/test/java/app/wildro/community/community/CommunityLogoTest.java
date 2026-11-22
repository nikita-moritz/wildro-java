package app.wildro.community.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import app.wildro.community.FakePeople;
import app.wildro.community.TestcontainersConfiguration;

/** A group's picture (§18.3, 2026-11-22): who sets it, what is taken, and moderation's part. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityLogoTest {

	@TempDir
	static Path media;

	@DynamicPropertySource
	static void mediaFolder(DynamicPropertyRegistry registry) {
		registry.add("wildro.media.dir", () -> media.toString());
		registry.add("wildro.media.base-url", () -> "https://java.wildro.app/media/communities/");
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private FakePeople people;

	@BeforeEach
	void clean() throws Exception {
		this.jdbc.sql("TRUNCATE communities, audit_log, outbox, consumer_cursor RESTART IDENTITY CASCADE").update();
		this.people.reset();
		try (Stream<Path> files = Files.list(media)) {
			for (Path file : files.toList()) {
				Files.delete(file);
			}
		}
		create("owner", "Group");
	}

	@Test
	void theOwnerSetsAPictureAndThePublicSeesItsAddress() throws Exception {
		upload("owner", webp("one")).andExpect(status().isOk())
			.andExpect(jsonPath("$.logoUrl").value(org.hamcrest.Matchers.matchesPattern(
					"https://java\\.wildro\\.app/media/communities/1-[0-9a-f]{12}\\.webp")));
		assertThat(files()).hasSize(1);
		this.mvc.perform(get("/v1/communities/group")).andExpect(jsonPath("$.logoUrl").isString());
		this.mvc.perform(get("/v1/communities")).andExpect(jsonPath("$.items[0].logoUrl").isString());
		assertThat(count("audit_log WHERE action = 'LOGO_CHANGED' AND actor = 'owner'")).isEqualTo(1);
	}

	@Test
	void onlyWebpOfAModestSizeIsTaken() throws Exception {
		upload("owner", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("INVALID"));
		upload("owner", webp("x".repeat(LogoFiles.MAX_BYTES))).andExpect(jsonPath("$.code").value("INVALID"));
		assertThat(files()).isEmpty();
	}

	@Test
	void aMemberMayNotButAnAdminMay() throws Exception {
		this.mvc.perform(post("/v1/communities/group/join").with(person("ada")));
		upload("ada", webp("ada")).andExpect(status().isForbidden());
		this.mvc.perform(post("/v1/communities/group/members/ada/role").with(person("owner"))
			.contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"));
		upload("ada", webp("ada")).andExpect(status().isOk());
	}

	@Test
	void aNewPictureReplacesTheOldFile() throws Exception {
		upload("owner", webp("one"));
		upload("owner", webp("two"));
		assertThat(files()).hasSize(1);
		this.mvc.perform(post("/v1/communities/group/logo/remove").with(person("owner")))
			.andExpect(jsonPath("$.logoUrl").doesNotExist());
		assertThat(files()).isEmpty();
	}

	@Test
	void moderationTakesThePictureAloneDownAndTellsTheOwner() throws Exception {
		removeByModeration("Spam").andExpect(jsonPath("$.code").value("WRONG_STATE"));
		upload("owner", webp("one"));

		this.mvc.perform(post("/v1/moderation/communities/group/logo/remove").with(person("ada"))
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Spam\"}")).andExpect(status().isForbidden());
		removeByModeration("Erkennbare Personen ohne Einwilligung").andExpect(status().isNoContent());

		assertThat(files()).isEmpty();
		this.mvc.perform(get("/v1/communities/group"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.logoUrl").doesNotExist());
		assertThat(count("outbox WHERE type = 'community.logo_removed' AND payload->>'ownerUserId' = 'owner'"))
			.isEqualTo(1);
		assertThat(count("audit_log WHERE action = 'LOGO_REMOVED' AND actor = 'mod'")).isEqualTo(1);
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
		return this.mvc.perform(put("/v1/communities/group/logo").with(person(subject))
			.contentType("image/webp")
			.content(bytes));
	}

	private ResultActions removeByModeration(String reason) throws Exception {
		return this.mvc.perform(post("/v1/moderation/communities/group/logo/remove").with(person("mod", "moderator"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"" + reason + "\"}"));
	}

	private void create(String subject, String name) throws Exception {
		this.mvc.perform(post("/v1/communities").with(person(subject))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"%s","description":"Wir wandern sonntags.","area":"Aachen","locale":"de","links":[]}
					""".formatted(name)));
	}

	private List<Path> files() throws IOException {
		try (Stream<Path> files = Files.list(media)) {
			return files.filter((file) -> file.getFileName().toString().endsWith(".webp")).toList();
		}
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
