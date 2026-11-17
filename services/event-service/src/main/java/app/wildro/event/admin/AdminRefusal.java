package app.wildro.event.admin;

/**
 * An admin action that was refused before anything changed: the event is not
 * there, the confirmation does not match, a reason is missing. It carries a
 * stable code so the caller, often a model, can branch on it instead of parsing
 * prose.
 */
public class AdminRefusal extends RuntimeException {

	private final Code code;

	public AdminRefusal(Code code, String message) {
		super(message);
		this.code = code;
	}

	public Code getCode() {
		return this.code;
	}

	public enum Code {

		NOT_FOUND,

		/** The confirmation did not repeat the slug exactly. */
		CONFIRMATION_MISMATCH,

		REASON_REQUIRED

	}

}
