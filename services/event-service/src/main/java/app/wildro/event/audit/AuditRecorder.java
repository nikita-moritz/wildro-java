package app.wildro.event.audit;

import app.wildro.event.domain.Event;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Map;

/**
 * Writes audit entries for events.
 * <p>
 * {@link Propagation#MANDATORY} on purpose: an audit entry is only meaningful as
 * part of the change it describes. Recorded in the same transaction, it commits
 * with the change and rolls back with it, so the log can never claim something
 * happened that did not, nor miss something that did.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AuditRecorder {

	private static final String EVENT = "EVENT";

	private final AuditLog log;

	private final JsonMapper json;

	private final Clock clock;

	AuditRecorder(AuditLog log, JsonMapper json, Clock clock) {
		this.log = log;
		this.json = json;
		this.clock = clock;
	}

	public void record(Operator operator, AuditAction action, Event event, Map<String, ?> details) {
		String serialised = details == null || details.isEmpty() ? null : this.json.writeValueAsString(details);
		this.log.save(new AuditEntry(this.clock.instant(), operator, action, EVENT,
				event.getId(), event.getSlug(), serialised));
	}

}
