package app.wildro.community.people;

import org.springframework.security.oauth2.jwt.Jwt;

/** Asks the user service about the person acting, at the moment they act (SPEC.md §18.4). */
public interface PeopleDirectory {

	/**
	 * @throws PeopleUnavailableException if the user service could not answer
	 */
	Person whoIs(Jwt token);

}
