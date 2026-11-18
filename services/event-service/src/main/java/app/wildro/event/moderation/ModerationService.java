package app.wildro.event.moderation;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.wildro.event.audit.AuditAction;
import app.wildro.event.audit.AuditRecorder;
import app.wildro.event.audit.Operator;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import tools.jackson.databind.json.JsonMapper;

/**
 * Notice and action, SPEC.md §18.2 and DSA Art. 16 and 17: anyone reports, a
 * moderator decides, the owner of what was taken down learns why. No
 * pre-moderation; the limits on writing and these reports are the protection.
 */
@Service
public class ModerationService {

	private final JdbcClient jdbc;

	private final EventRepository events;

	private final AuditRecorder audit;

	private final JsonMapper json;

	private final Clock clock;

	ModerationService(JdbcClient jdbc, EventRepository events, AuditRecorder audit, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.events = events;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	/** Only what the public can see can be reported: a published event. */
	@Transactional
	public void report(String slug, ReportReason reason, String details, String reporterUserId) {
		Event event = this.events.findBySlug(slug)
			.filter((found) -> found.getStatus() == EventStatus.PUBLISHED)
			.orElseThrow(() -> new ModerationRefusal(ModerationRefusal.Code.NOT_FOUND, "no such public event"));
		this.jdbc.sql("""
				INSERT INTO reports (event_id, reason, details, reporter_user_id, created_at)
				VALUES (:event, :reason, :details, :reporter, :at)
				""")
			.param("event", event.getId())
			.param("reason", reason.name())
			.param("details", details.strip())
			.param("reporter", reporterUserId)
			.param("at", Timestamp.from(this.clock.instant()))
			.update();
	}

	@Transactional(readOnly = true)
	public List<OpenReport> openReports() {
		return this.jdbc.sql("""
				SELECT r.id, r.reason, r.details, r.created_at, e.slug, e.kind, e.status, t.name, e.organizer
				FROM reports r
				JOIN events e ON e.id = r.event_id
				JOIN event_translations t ON t.event_id = e.id AND t.locale = coalesce(e.original_locale, 'de')
				WHERE r.status = 'OPEN'
				ORDER BY r.created_at
				LIMIT 200
				""")
			.query((rs, n) -> new OpenReport(rs.getLong("id"), ReportReason.valueOf(rs.getString("reason")),
					rs.getString("details"), rs.getTimestamp("created_at").toInstant(), rs.getString("slug"),
					rs.getString("kind"), rs.getString("status"), rs.getString("name"), rs.getString("organizer")))
			.list();
	}

	/**
	 * Takes the event off the site, closes its open reports as acted on, and
	 * queues the statement of reasons for its owner in the same transaction, so
	 * the removal and the notice cannot part ways.
	 */
	@Transactional
	public void suspend(String slug, String reason, String moderatorId) {
		Event event = find(slug);
		if (event.getStatus() != EventStatus.PUBLISHED) {
			throw new ModerationRefusal(ModerationRefusal.Code.WRONG_STATE, "only a published event is taken down");
		}
		event.suspend(reason);
		Instant now = this.clock.instant();
		this.jdbc.sql("""
				UPDATE reports SET status = 'ACTIONED', resolved_at = :at, resolved_by = :by
				WHERE event_id = :event AND status = 'OPEN'
				""").param("at", Timestamp.from(now)).param("by", moderatorId).param("event", event.getId()).update();
		if (event.getOwnerUserId() != null) {
			this.jdbc.sql("""
					INSERT INTO notice_outbox (kind, recipient_user_id, event_id, payload)
					VALUES ('EVENT_SUSPENDED', :to, :event, CAST(:payload AS jsonb))
					""")
				.param("to", event.getOwnerUserId())
				.param("event", event.getId())
				.param("payload", this.json.writeValueAsString(Map.of("slug", event.getSlug(), "reason",
						event.getSuspensionReason(), "decidedAt", now.toString())))
				.update();
		}
		this.audit.record(Operator.web(moderatorId), AuditAction.EVENT_SUSPENDED, event,
				Map.of("reason", event.getSuspensionReason()));
	}

	@Transactional
	public void reinstate(String slug, String moderatorId) {
		Event event = find(slug);
		if (event.getStatus() != EventStatus.SUSPENDED) {
			throw new ModerationRefusal(ModerationRefusal.Code.WRONG_STATE, "only a suspended event is put back");
		}
		event.reinstate();
		this.audit.record(Operator.web(moderatorId), AuditAction.EVENT_REINSTATED, event, Map.of());
	}

	@Transactional
	public void dismiss(long reportId, String moderatorId) {
		Long eventId = this.jdbc.sql("SELECT event_id FROM reports WHERE id = :id AND status = 'OPEN'")
			.param("id", reportId)
			.query(Long.class)
			.optional()
			.orElseThrow(() -> new ModerationRefusal(ModerationRefusal.Code.NOT_FOUND, "no such open report"));
		this.jdbc.sql("""
				UPDATE reports SET status = 'DISMISSED', resolved_at = :at, resolved_by = :by WHERE id = :id
				""").param("at", Timestamp.from(this.clock.instant())).param("by", moderatorId).param("id", reportId).update();
		this.events.findById(eventId)
			.ifPresent((event) -> this.audit.record(Operator.web(moderatorId), AuditAction.REPORT_DISMISSED, event,
					Map.of("report", reportId)));
	}

	private Event find(String slug) {
		return this.events.findBySlug(slug)
			.orElseThrow(() -> new ModerationRefusal(ModerationRefusal.Code.NOT_FOUND, "no such event"));
	}

	public record OpenReport(long id, ReportReason reason, String details, Instant createdAt, String slug, String kind,
			String eventStatus, String eventName, String organizer) {
	}

}
