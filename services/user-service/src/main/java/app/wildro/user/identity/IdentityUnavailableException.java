package app.wildro.user.identity;

/** elvix did not answer, or answered something other than a person or "unknown". */
public class IdentityUnavailableException extends RuntimeException {

	public IdentityUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
