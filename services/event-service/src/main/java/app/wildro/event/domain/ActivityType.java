package app.wildro.event.domain;

/**
 * What kind of outing this is. The vocabulary comes from the working version of
 * wildro, where it has been in use on real data and is already translated into
 * all three languages, so importing the existing events later needs no mapping
 * table.
 * <p>
 * Held as text in the database behind a check constraint rather than as a
 * PostgreSQL enum type: both give the same guarantee, but only one of them lets
 * a value be renamed or retired without rewriting the type and everything that
 * depends on it. This enum is the source of truth; the constraint mirrors it.
 */
public enum ActivityType {

	HIKING,
	TRAIL_RUN,
	CYCLING,
	WATER,
	MOUNTAINEERING,
	CLIMBING,
	FERRATA,
	KIDS,
	YOGA,
	CAMPING

}
