package app.wildro.event.audit;

/**
 * What happened. Creation, revision, cancellation and deletion of an event are
 * always recorded (SPEC.md §0.1). The check constraint in V9 mirrors this list.
 */
public enum AuditAction {

	EVENT_CREATED,
	EVENT_UPDATED,
	EVENT_CANCELLED,
	EVENT_DELETED,
	EVENT_PUBLISHED,
	EVENT_SUSPENDED,
	EVENT_REINSTATED,
	REPORT_DISMISSED

}
