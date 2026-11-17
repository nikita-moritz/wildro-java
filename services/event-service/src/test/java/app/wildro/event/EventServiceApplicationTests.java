package app.wildro.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EventServiceApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	/**
	 * Proves the pinned image really carries PostGIS and that Flyway created the
	 * extension. Without this the first geography column is where we would find out,
	 * by then several migrations deep.
	 */
	@Test
	void postgisExtensionIsInstalledByMigration() {
		Integer installed = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_extension WHERE extname = 'postgis'", Integer.class);

		assertThat(installed).isEqualTo(1);
	}

	@Test
	void rootPageReportsTheRunningRevision() throws Exception {
		this.mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("revision:")));
	}

	@Test
	void healthEndpointIsUp() throws Exception {
		this.mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}

}
