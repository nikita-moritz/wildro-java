package app.wildro.event.own;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Asks the user service about the person writing (SPEC.md §18.4: the right to
 * write is checked on the server, on every write, at the moment of writing).
 */
public interface WriterDirectory {

	/**
	 * @throws WriterUnavailableException if the user service could not answer
	 */
	Writer whoIs(Jwt token);

}
