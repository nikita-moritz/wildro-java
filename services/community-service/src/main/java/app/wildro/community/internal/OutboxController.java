package app.wildro.community.internal;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /internal/outbox?after=N}: community.closed for the event service, pulled in order. */
@RestController
class OutboxController {

	private final JdbcClient jdbc;

	private final InternalToken token;

	OutboxController(JdbcClient jdbc, InternalToken token) {
		this.jdbc = jdbc;
		this.token = token;
	}

	@GetMapping("/internal/outbox")
	ResponseEntity<List<Entry>> after(@RequestHeader(name = "X-Internal-Token", required = false) String presented,
			@RequestParam(defaultValue = "0") long after, @RequestParam(defaultValue = "100") int limit) {
		if (!this.token.matches(presented)) {
			return ResponseEntity.status(401).build();
		}
		return ResponseEntity.ok(this.jdbc.sql("""
				SELECT id, type, community_id, payload::text AS payload, created_at FROM outbox
				WHERE id > :after ORDER BY id LIMIT :limit
				""")
			.param("after", after)
			.param("limit", Math.min(Math.max(limit, 1), 500))
			.query((rs, n) -> new Entry(rs.getLong("id"), rs.getString("type"), String.valueOf(rs.getLong("community_id")),
					rs.getString("payload"), rs.getTimestamp("created_at").toInstant().toString()))
			.list());
	}

	record Entry(long id, String type, String subject, String payload, String createdAt) {
	}

}
