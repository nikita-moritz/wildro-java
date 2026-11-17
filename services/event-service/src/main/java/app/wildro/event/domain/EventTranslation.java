package app.wildro.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * The translated strings of one event in one language.
 * <p>
 * The place in words sits here beside the name and the description rather than
 * on the event itself, because a card must not mix languages. The working
 * version put an English date next to a Russian country name, which is the
 * defect SPEC.md §0 explicitly tells us not to carry over.
 * <p>
 * A value object: two translations with the same text are the same translation,
 * which is what lets a repeated import recognise that nothing changed.
 */
@Embeddable
public class EventTranslation {

	@Column(nullable = false)
	private String name;

	@Column(name = "place_name", nullable = false)
	private String placeName;

	@Column(nullable = false)
	private String description;

	protected EventTranslation() {
		// for JPA
	}

	public EventTranslation(String name, String placeName, String description) {
		this.name = requireText(name, "name");
		this.placeName = requireText(placeName, "placeName");
		this.description = requireText(description, "description");
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new InvalidEventException(field, "is required and must not be blank");
		}
		return value.strip();
	}

	public String getName() {
		return this.name;
	}

	public String getPlaceName() {
		return this.placeName;
	}

	public String getDescription() {
		return this.description;
	}

	@Override
	public boolean equals(Object other) {
		return this == other || (other instanceof EventTranslation that
				&& this.name.equals(that.name)
				&& this.placeName.equals(that.placeName)
				&& this.description.equals(that.description));
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.placeName, this.description);
	}

}
