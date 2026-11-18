package app.wildro.user.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import app.wildro.user.TestcontainersConfiguration;

/** The outbox other services pull from: only with the shared token, in order, after a cursor. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxControllerTest {

	private static final String TOKEN = "test-internal-token-0123456789abcdef";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void seed() {
		this.jdbc.sql("DELETE FROM outbox").update();
		for (String user : new String[] { "u1", "u2" }) {
			this.jdbc.sql("INSERT INTO outbox (type, user_id, payload) VALUES ('user.erased', :u, '{}'::jsonb)")
				.param("u", user)
				.update();
		}
	}

	@Test
	void withoutTheTokenNothingIsRead() throws Exception {
		this.mvc.perform(get("/internal/outbox")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/internal/outbox").header("X-Internal-Token", "wrong")).andExpect(status().isUnauthorized());
	}

	@Test
	void entriesComeInOrderAfterTheCursor() throws Exception {
		long first = this.jdbc.sql("SELECT min(id) FROM outbox").query(Long.class).single();
		this.mvc.perform(get("/internal/outbox").header("X-Internal-Token", TOKEN).param("after", String.valueOf(first)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].subject").value("u2"))
			.andExpect(jsonPath("$[0].type").value("user.erased"));
	}

}
