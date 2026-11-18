package app.wildro.user.internal;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /internal/outbox?after=N}: what this service told the others, in
 * order (SPEC.md §18.4). Consumers pull and keep their own cursor in the same
 * transaction as what they do with an entry, so a consumer that was down
 * catches up later, and none applies an entry twice. Rows are never deleted
 * here: the log is small and is the history of every erasure.
 */
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
				SELECT id, type, user_id, payload::text AS payload, created_at FROM outbox
				WHERE id > :after ORDER BY id LIMIT :limit
				""")
			.param("after", after)
			.param("limit", Math.min(Math.max(limit, 1), 500))
			.query((rs, n) -> new Entry(rs.getLong("id"), rs.getString("type"), rs.getString("user_id"),
					rs.getString("payload"), rs.getTimestamp("created_at").toInstant().toString()))
			.list());
	}

	record Entry(long id, String type, String subject, String payload, String createdAt) {
	}

}
