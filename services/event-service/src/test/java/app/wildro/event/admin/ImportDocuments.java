package app.wildro.event.admin;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * A valid import document to start every test from, so each test states only
 * the one thing it breaks. Dates sit a month ahead so the event is upcoming
 * whenever the suite runs.
 */
final class ImportDocuments {

	static final JsonMapper JSON = JsonMapper.builder().build();

	/**
	 * RFC 3339 with seconds always written. {@link OffsetDateTime#toString()} drops
	 * the seconds when they are zero, and "15:00+02:00" is not a valid date-time:
	 * the schema refuses it, correctly.
	 */
	private static final DateTimeFormatter RFC_3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

	private ImportDocuments() {
	}

	static ObjectNode single(String slug) {
		ObjectNode root = JSON.createObjectNode();
		root.putArray("events").add(event(slug));
		return root;
	}

	static ObjectNode batch(String... slugs) {
		ObjectNode root = JSON.createObjectNode();
		ArrayNode events = root.putArray("events");
		for (String slug : slugs) {
			events.add(event(slug));
		}
		return root;
	}

	static ObjectNode event(String slug) {
		OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(2)).plusMonths(1).truncatedTo(ChronoUnit.HOURS);
		return (ObjectNode) JSON.readTree("""
				{
				  "slug": "%s",
				  "status": "PUBLISHED",
				  "activityType": "HIKING",
				  "difficulty": "MEDIUM",
				  "startsAt": "%s",
				  "startPoint": { "lat": 50.7753, "lon": 6.0839 },
				  "organizer": "Eifelverein Aachen",
				  "source": { "url": "https://example.org/gruppe/%s", "readAt": "%s" },
				  "distanceKm": 18.5,
				  "elevationGainM": 420,
				  "translations": {
				    "de": { "name": "Eifelsteig Etappe 1", "placeName": "Aachen, Deutschland", "description": "Auftakt am Dom." },
				    "en": { "name": "Eifelsteig stage 1", "placeName": "Aachen, Germany", "description": "The opening leg from the cathedral." },
				    "ru": { "name": "Айфельштайг, этап 1", "placeName": "Ахен, Германия", "description": "Первый отрезок от собора." }
				  }
				}
				""".formatted(slug, rfc3339(start), slug, rfc3339(start.minusDays(3))));
	}

	static String rfc3339(OffsetDateTime dateTime) {
		return RFC_3339.format(dateTime);
	}

	/** The first event of a document, for editing in place. */
	static ObjectNode first(JsonNode root) {
		return (ObjectNode) root.at("/events/0");
	}

}
