package app.wildro.community.community;

import org.springframework.http.HttpStatus;

/** Why an action was refused, as a stable code the site turns into a sentence. Nothing was written. */
public class CommunityRefusal extends RuntimeException {

	public enum Code {

		NOT_FOUND(HttpStatus.NOT_FOUND),
		FORBIDDEN(HttpStatus.FORBIDDEN),
		TERMS_REQUIRED(HttpStatus.CONFLICT),
		DISPLAY_NAME_REQUIRED(HttpStatus.CONFLICT),
		LIMIT_OWNED(HttpStatus.CONFLICT),
		INVALID(HttpStatus.UNPROCESSABLE_CONTENT),
		WRONG_STATE(HttpStatus.CONFLICT),
		OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT),
		TARGET_NOT_ADMIN(HttpStatus.CONFLICT),
		TOO_MANY(HttpStatus.TOO_MANY_REQUESTS),
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

	public CommunityRefusal(Code code, String message) {
		super(message);
		this.code = code;
	}

	public Code code() {
		return this.code;
	}

}
