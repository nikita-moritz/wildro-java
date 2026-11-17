package app.wildro.analytics.track;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import app.wildro.analytics.TestcontainersConfiguration;

/** The dev switch: even a view on the site host is dropped. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "analytics.salt=test-salt", "analytics.disabled=true" })
@AutoConfigureMockMvc
class DisabledTrackingTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void nothingIsStored() throws Exception {
		this.jdbc.sql("DELETE FROM page_view").update();
		this.mvc.perform(post("/v1/track").contentType(MediaType.APPLICATION_JSON).content("""
				{"path":"/de","host":"wildro.app","clientIp":"203.0.113.7","userAgent":"Mozilla/5.0 Chrome/141"}"""))
			.andExpect(status().isAccepted());

		assertThat(this.jdbc.sql("SELECT count(*) FROM page_view").query(Long.class).single()).isZero();
	}

}
