package app.wildro.community.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import app.wildro.community.TestcontainersConfiguration;

/** user.erased from the user service: applied once with the cursor, and the group's cached page dropped. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class UserErasureConsumerTest {

	@Autowired
	private UserErasureConsumer consumer;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private RecordingInvalidator cache;

	@BeforeEach
	void seed() {
		this.jdbc.sql("TRUNCATE communities, audit_log, outbox, consumer_cursor RESTART IDENTITY CASCADE").update();
		this.cache.reset();
		this.jdbc.sql("INSERT INTO communities (slug, name, description, area, locale) VALUES ('group', 'Group', 'We hike.', 'Aachen', 'de')").update();
		this.jdbc.sql("INSERT INTO memberships (community_id, user_id, role, display_name) VALUES (1, 'owner', 'OWNER', 'Owner')").update();
	}

	@Test
	void anErasedOwnersLonelyGroupClosesTheCursorMovesAndThePageIsDropped() {
		this.consumer.consume(List.of(new UserErasureConsumer.Entry(42, "user.erased", "owner", "{}", "2026-11-19T10:00:00Z")));

		assertThat(this.jdbc.sql("SELECT status FROM communities").query(String.class).single()).isEqualTo("CLOSED");
		assertThat(this.consumer.cursor()).isEqualTo(42);
		assertThat(this.cache.tags()).containsExactlyInAnyOrder("communities:list", "community:group");
	}

}
