package app.wildro.user.account;

/**
 * The rule for a display name, in one place: the column's check constraint is
 * its last line of defence, this is the one people meet.
 */
final class DisplayName {

	static final int MIN = 2;

	static final int MAX = 50;

	private DisplayName() {
	}

	/** Trims and collapses inner whitespace; null if what is left is not a name. */
	static String normalise(String raw) {
		if (raw == null) {
			return null;
		}
		String name = raw.strip().replaceAll("\\s+", " ");
		// Code points, as the database's char_length counts: one emoji is two
		// chars in Java and one character there.
		int length = name.codePointCount(0, name.length());
		if (length < MIN || length > MAX || name.codePoints().anyMatch(Character::isISOControl)) {
			return null;
		}
		return name;
	}

}
