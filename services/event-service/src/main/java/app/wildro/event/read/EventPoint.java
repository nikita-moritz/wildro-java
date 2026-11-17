package app.wildro.event.read;

import app.wildro.event.domain.ActivityType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One marker on the map and its row in the list beside it. The third read form
 * of AGENTS.md: identity, coordinates, and what the marker and the row draw,
 * which is the name, the place in words, the dates and the cover. Still no
 * description, organiser or numbers: the map answers per rectangle, often, and
 * the page of the event is one click away.
 */
public record EventPoint(
		String slug,
		String name,
		String placeName,
		ActivityType activityType,
		Instant startsAt,
		Instant endsAt,
		String coverImageUrl,
		String coverCredit,
		BigDecimal priceAmount,
		String priceCurrency,
		double latitude,
		double longitude) {
}
