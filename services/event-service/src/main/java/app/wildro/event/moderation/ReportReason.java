package app.wildro.event.moderation;

/** Why someone reports an event. V9's check constraint mirrors this list. */
public enum ReportReason {

	SPAM,
	MISLEADING,
	DANGEROUS,
	ILLEGAL,
	PERSONAL_DATA,
	OTHER

}
