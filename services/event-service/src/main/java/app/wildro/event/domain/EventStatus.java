package app.wildro.event.domain;

/**
 * Whether the event is visible, and how.
 * <p>
 * Note what is absent: "past". A stored flag needs something to keep it true and
 * would be wrong between one run of that something and the next. What is stored
 * instead is the moment the event passes, which depends only on its own dates;
 * whether that moment is behind us is asked at read time. The single definition
 * lives in {@link Event#hasPassed}.
 */
public enum EventStatus {

	/** Entered, not yet on the public side. */
	DRAFT,

	PUBLISHED,

	/**
	 * We learned the source called it off. The page stays at its address for the
	 * year SPEC.md §0.3 promises, leaves the upcoming feed, and says so in its
	 * machine-readable data.
	 */
	CANCELLED

}
