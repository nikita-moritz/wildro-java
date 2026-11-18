package app.wildro.event.read;

import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Difficulty;
import app.wildro.event.domain.EventKind;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.LinkNetwork;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything the page of one event shows, in one language.
 * <p>
 * {@code passesAt} rather than a "past" flag: a flag would change with the clock
 * while nothing was written, so a cached page would keep claiming the event is
 * ahead. The moment is fixed; whoever renders compares it with the time.
 * <p>
 * Capacity is absent on purpose. Every stage-one event is external and never
 * shows one.
 * <p>
 * Both lists of links are always present and usually empty. Each link says which
 * network it belongs to, so the page picks a mark without parsing addresses.
 */
public record EventPage(
		long id,
		String slug,
		EventStatus status,
		String name,
		String placeName,
		String description,
		ActivityType activityType,
		Difficulty difficulty,
		Instant startsAt,
		Instant endsAt,
		Instant passesAt,
		double latitude,
		double longitude,
		String organizer,
		String sourceUrl,
		Instant sourceCheckedAt,
		BigDecimal distanceKm,
		Integer elevationGainM,
		String coverImageUrl,
		Credit coverCredit,
		Price price,
		List<Link> eventLinks,
		List<Link> communityLinks,
		/** OWN: organised on wildro, no source to link out to (SPEC.md §18.2). */
		EventKind kind,
		/** The language an own event was written in; null for an external one. */
		String originalLocale,
		/** The community that owns it, for a link to its page; null otherwise. */
		String communitySlug,
		Instant updatedAt) {

	public record Link(LinkNetwork network, String url) {
	}

	/** The regular adult price, zero when free; null when the source names none. */
	public record Price(BigDecimal amount, String currency) {
	}

	/** Shown beside the cover; null when there is no cover. */
	public record Credit(String author, String license, String licenseUrl, String sourceUrl) {
	}

}
