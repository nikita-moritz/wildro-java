package app.wildro.event.own;

import org.springframework.security.oauth2.jwt.Jwt;

/** Asks the community service about the caller's standing, at the moment of writing (SPEC.md §18.4). */
public interface CommunityGateway {

	/** The caller's standing in the community; {@code role} null when not a member. */
	record Standing(long id, String slug, String name, String status, String role) {

		public boolean manages() {
			return "ACTIVE".equals(this.status) && ("OWNER".equals(this.role) || "ADMIN".equals(this.role));
		}

	}

	/**
	 * @return the standing, or null if there is no such community
	 * @throws WriterUnavailableException if the community service could not answer
	 */
	Standing standing(Jwt token, String slug);

}
