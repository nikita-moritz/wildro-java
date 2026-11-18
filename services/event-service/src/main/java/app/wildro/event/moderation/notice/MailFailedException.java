package app.wildro.event.moderation.notice;

public class MailFailedException extends RuntimeException {

	public MailFailedException(String message, Throwable cause) {
		super(message, cause);
	}

}
