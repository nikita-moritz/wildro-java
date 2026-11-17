package app.wildro.event.admin;

import app.wildro.event.audit.AuditAction;
import app.wildro.event.audit.AuditRecorder;
import app.wildro.event.audit.Operator;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Removes an event and its address for good.
 * <p>
 * Reserved for junk and mistakes, never for "the date has passed": a past page
 * stays reachable for at least a year and removing it is a separate decision
 * with a reason (SPEC.md §0.3). The reason is therefore required and goes into
 * the audit log, which outlives the row.
 */
@Service
public class EventDeleter {

	private final EventRepository events;

	private final AuditRecorder audit;

	EventDeleter(EventRepository events, AuditRecorder audit) {
		this.events = events;
		this.audit = audit;
	}

	@Transactional
	public void delete(String slug, String confirmation, String reason, Operator operator) {
		Event event = this.events.findBySlug(slug)
				.orElseThrow(() -> new AdminRefusal(AdminRefusal.Code.NOT_FOUND, "no event with slug " + slug));
		Confirmation.require(slug, confirmation);
		if (reason == null || reason.isBlank()) {
			throw new AdminRefusal(AdminRefusal.Code.REASON_REQUIRED,
					"deleting an address needs a reason; it is kept in the audit log");
		}
		// Recorded before the row goes, while its id is still there to record.
		this.audit.record(operator, AuditAction.EVENT_DELETED, event, Map.of("reason", reason.strip()));
		this.events.delete(event);
	}

}
