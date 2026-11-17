package app.wildro.event.domain;

/**
 * How hard the outing is, as far as the admin can tell from the source. Three
 * levels rather than more: on an external event this is judged by eye from
 * someone else's description, and finer gradations would claim a precision the
 * input does not carry. Optional, because plenty of sources say nothing about it
 * and inventing a value is worse than leaving it out.
 */
public enum Difficulty {

	EASY,
	MEDIUM,
	HARD

}
