package app.wildro.event.domain;

/**
 * An event that breaks one of its own rules.
 * <p>
 * It carries the offending field as a path relative to one event, such as
 * {@code endsAt} or {@code translations/ru/name}, because SPEC.md §0.1 requires a
 * validation error to name the field and the reason machine-readably: the next
 * attempt of whoever wrote the document, often a language model, has to be a
 * correction rather than another guess.
 */
public class InvalidEventException extends IllegalArgumentException {

	private final String field;

	private final String reason;

	public InvalidEventException(String field, String reason) {
		super(field + ": " + reason);
		this.field = field;
		this.reason = reason;
	}

	public String getField() {
		return this.field;
	}

	public String getReason() {
		return this.reason;
	}

}
