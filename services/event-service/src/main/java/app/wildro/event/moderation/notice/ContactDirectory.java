package app.wildro.event.moderation.notice;

import java.util.Optional;

/** Where to write to a person, asked for at the moment of sending and never stored here. */
public interface ContactDirectory {

	/**
	 * @return the address, or empty if the account no longer exists
	 * @throws MailFailedException if the directory could not be asked
	 */
	Optional<String> emailOf(String userId);

}
