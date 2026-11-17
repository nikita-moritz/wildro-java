package app.wildro.event.audit;

/**
 * How the change arrived. Only the command line exists on stage one; the panel
 * is recorded here already so its entries sit in the same log from day one.
 */
public enum AuditChannel {

	CLI,
	PANEL

}
