package app.wildro.event.moderation;

import org.springframework.http.HttpStatus;

/** Why a report or a moderation step was refused. Nothing was written. */
public class ModerationRefusal extends RuntimeException {

	public enum Code {

		NOT_FOUND(HttpStatus.NOT_FOUND), WRONG_STATE(HttpStatus.CONFLICT), TOO_MANY(HttpStatus.TOO_MANY_REQUESTS);

		private final HttpStatus status;

		Code(HttpStatus status) {
			this.status = status;
		}

		public HttpStatus status() {
			return this.status;
		}

	}

	private final Code code;

	public ModerationRefusal(Code code, String message) {
		super(message);
		this.code = code;
	}

	public Code code() {
		return this.code;
	}

}
