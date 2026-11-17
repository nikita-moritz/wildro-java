package app.wildro.event.admin;

import app.wildro.event.audit.AuditAction;
import app.wildro.event.audit.AuditRecorder;
import app.wildro.event.audit.Operator;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Marks an event cancelled once we learn the source called it off (SPEC.md §0,
 * open question 3). The page stays at its address for the promised year, leaves
 * the upcoming feed, and says so in its machine-readable data.
 * <p>
 * A separate command and never a field in the import document, so a cancellation
 * is always somebody's deliberate act and is never undone by importing the same
 * event again.
 */
@Service
public class EventCanceller {

	private final EventRepository events;

	private final AuditRecorder audit;

	EventCanceller(EventRepository events, AuditRecorder audit) {
		this.events = events;
		this.audit = audit;
	}

	/** @return {@code false} if the event was already cancelled, in which case nothing is written. */
	@Transactional
	public boolean cancel(String slug, String confirmation, Operator operator) {
		Event event = this.events.findBySlug(slug)
				.orElseThrow(() -> new AdminRefusal(AdminRefusal.Code.NOT_FOUND, "no event with slug " + slug));
		Confirmation.require(slug, confirmation);
		if (event.getStatus() == EventStatus.CANCELLED) {
			return false;
		}
		EventStatus before = event.getStatus();
		event.cancel();
		this.audit.record(operator, AuditAction.EVENT_CANCELLED, event, Map.of("previousStatus", before));
		return true;
	}

}
