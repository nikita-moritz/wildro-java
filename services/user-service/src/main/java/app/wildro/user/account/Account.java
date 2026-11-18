package app.wildro.user.account;

import java.time.Instant;

/** A person as wildro holds them: SPEC.md §18.1, three facts and the terms mark. */
public record Account(String id, String email, String displayName, String status, String termsVersion,
		Instant termsAcceptedAt, Instant erasedAt) {

	public boolean erased() {
		return this.erasedAt != null;
	}

}
