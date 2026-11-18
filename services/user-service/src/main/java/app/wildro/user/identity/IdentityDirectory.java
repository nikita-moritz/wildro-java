package app.wildro.user.identity;

import java.util.Optional;

/**
 * Looks a person up at the identity provider by the subject of a verified token.
 * The address and the name are read from there, never taken from the caller:
 * the caller is trusted with the token, not with what it says about the person.
 */
public interface IdentityDirectory {

	/**
	 * @return the person, or empty if elvix does not know them in this application
	 * @throws IdentityUnavailableException if elvix could not be asked
	 */
	Optional<Identity> find(String userId);

}
