package app.wildro.event.read;

import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Difficulty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One event in a list, in one language. The narrow read form the plan calls
 * {@code EventShort}: what a card shows and nothing more, read straight from a
 * query with a locale predicate so each event is one row.
 * <p>
 * No coordinates and no distance from the visitor. "N km from you" differs per
 * visitor, and the content behind one address must not (SPEC.md §0.2), so it is
 * computed in the browser from the map data, never served here.
 */
public record EventCard(
		String slug,
		String name,
		String placeName,
		ActivityType activityType,
		Difficulty difficulty,
		Instant startsAt,
		Instant endsAt,
		String organizer,
		BigDecimal distanceKm,
		Integer elevationGainM,
		String coverImageUrl,
		/** "Author, licence", for the title of a small cover; the page shows the full credit. */
		String coverCredit,
		/** The regular adult price, zero when free; both null when the source names none. */
		BigDecimal priceAmount,
		String priceCurrency) {
}
