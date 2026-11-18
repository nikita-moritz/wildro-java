package app.wildro.event.moderation.notice;

/** Sends one plain-text email. */
public interface Mailer {

	/** False while no transport is configured; notices then wait in the outbox. */
	boolean enabled();

	/**
	 * @throws MailFailedException if the transport refused or did not answer
	 */
	void send(String to, String subject, String text);

}
