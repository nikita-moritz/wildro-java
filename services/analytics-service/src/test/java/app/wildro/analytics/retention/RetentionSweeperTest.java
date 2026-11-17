package app.wildro.analytics.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import app.wildro.analytics.MutableClock;
import app.wildro.analytics.TestcontainersConfiguration;

/** Raw rows are deleted after fourteen months, SPEC.md §13. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "analytics.salt=test-salt")
class RetentionSweeperTest {

	@Autowired
	private RetentionSweeper sweeper;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Test
	void rowsOlderThanFourteenMonthsGoTheRestStay() {
		this.jdbc.sql("DELETE FROM page_view").update();
		this.clock.set(Instant.parse("2026-10-21T03:17:00Z"));
		insert(Instant.parse("2025-08-20T23:59:00Z"));
		insert(Instant.parse("2025-08-21T03:18:00Z"));

		assertThat(this.sweeper.sweep()).isEqualTo(1);
		assertThat(this.jdbc.sql("SELECT count(*) FROM page_view").query(Long.class).single()).isEqualTo(1);
	}

	private void insert(Instant at) {
		this.jdbc.sql("INSERT INTO page_view (viewed_at, path, host) VALUES (:at, '/de', 'wildro.app')")
			.param("at", Timestamp.from(at))
			.update();
	}

}
