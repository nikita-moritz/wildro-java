package app.wildro.event.admin;

import java.util.List;

/**
 * What an import did, or in a dry run what it would do.
 * <p>
 * Either {@code violations} is empty and {@code items} says what happens to each
 * event, or {@code violations} is not empty and nothing was written at all: a
 * batch applies wholly or not at all (SPEC.md §0.1).
 */
public record ImportOutcome(Mode mode, List<Violation> violations, List<Item> items) {

	public ImportOutcome {
		violations = List.copyOf(violations);
		items = List.copyOf(items);
	}

	static ImportOutcome refused(Mode mode, List<Violation> violations) {
		return new ImportOutcome(mode, violations, List.of());
	}

	public boolean ok() {
		return this.violations.isEmpty();
	}

	public long count(Action action) {
		return this.items.stream().filter(item -> item.action() == action).count();
	}

	public enum Mode {

		/** Checked and planned, nothing written. What every first run is. */
		DRY_RUN,

		WRITE

	}

	public enum Action {

		CREATE,
		UPDATE,

		/** Already exactly as the document says. Nothing written, nothing audited. */
		UNCHANGED

	}

	/** The fate of one event of the document, by its index there. */
	public record Item(int index, String slug, Action action, List<String> changedFields) {

		public Item {
			changedFields = List.copyOf(changedFields);
		}

	}

}
