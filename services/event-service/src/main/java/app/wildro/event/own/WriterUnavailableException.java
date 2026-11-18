package app.wildro.event.own;

/** The user service did not answer, so nobody can be allowed to write. */
public class WriterUnavailableException extends RuntimeException {

	public WriterUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
