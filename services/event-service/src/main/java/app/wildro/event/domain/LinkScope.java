package app.wildro.event.domain;

/**
 * What a link beside an event leads to. An event link is about this one outing:
 * its post, its chat, its page on a network. A community link is where the
 * people running it can be found whether or not this outing has passed.
 */
public enum LinkScope {

	EVENT, COMMUNITY;

	/** The key of this scope in the import document, and so the path of its errors. */
	public String documentKey() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

}
