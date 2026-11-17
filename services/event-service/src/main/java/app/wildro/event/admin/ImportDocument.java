package app.wildro.event.admin;

import java.math.BigDecimal;
import java.util.List;

/**
 * The shape of an import document, bound only after it has passed
 * {@link ImportSchema}. The schema file is the contract; these records are how
 * the code reads a document that already honours it.
 * <p>
 * Dates stay strings here and are parsed by the importer, so a value the schema
 * somehow let through still comes back as a violation on its field rather than
 * as a stack trace from the binder.
 */
public record ImportDocument(List<EventDocument> events) {

	public record EventDocument(
			String slug,
			String status,
			String activityType,
			String difficulty,
			String startsAt,
			String endsAt,
			StartPoint startPoint,
			String organizer,
			Source source,
			BigDecimal distanceKm,
			Integer elevationGainM,
			String coverImageUrl,
			Credit coverCredit,
			Price price,
			Links links,
			Translations translations) {
	}

	public record StartPoint(Double lat, Double lon) {
	}

	public record Source(String url, String readAt) {
	}

	public record Credit(String author, String license, String licenseUrl, String sourceUrl) {
	}

	public record Price(BigDecimal amount, String currency) {
	}

	/** Both lists are optional, and so is the object itself. */
	public record Links(List<String> event, List<String> community) {
	}

	public record Translations(Translation de, Translation en, Translation ru) {
	}

	public record Translation(String name, String placeName, String description) {
	}

}
