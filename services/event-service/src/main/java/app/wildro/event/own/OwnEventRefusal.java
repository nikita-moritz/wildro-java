package app.wildro.event.own;

import org.springframework.http.HttpStatus;

/**
 * Why a write by a person was refused, as a stable code the site turns into a
 * sentence in the reader's language. Nothing was written when one is thrown.
 */
public class OwnEventRefusal extends RuntimeException {

	public enum Code {

		NOT_FOUND(HttpStatus.NOT_FOUND),
		NOT_YOURS(HttpStatus.FORBIDDEN),
		TERMS_REQUIRED(HttpStatus.CONFLICT),
		DISPLAY_NAME_REQUIRED(HttpStatus.CONFLICT),
		LIMIT_PER_DAY(HttpStatus.TOO_MANY_REQUESTS),
		LIMIT_UPCOMING(HttpStatus.CONFLICT),
		IN_THE_PAST(HttpStatus.UNPROCESSABLE_CONTENT),
		WRONG_STATE(HttpStatus.CONFLICT),
		INVALID(HttpStatus.UNPROCESSABLE_CONTENT),
		UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

		private final HttpStatus status;

		Code(HttpStatus status) {
			this.status = status;
		}

		public HttpStatus status() {
			return this.status;
		}

	}

	private final Code code;

	public OwnEventRefusal(Code code, String message) {
		super(message);
		this.code = code;
	}

	public Code code() {
		return this.code;
	}

}
