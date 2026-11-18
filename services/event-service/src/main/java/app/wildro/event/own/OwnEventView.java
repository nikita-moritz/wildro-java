package app.wildro.event.own;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Difficulty;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventLink;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.EventTranslation;
import app.wildro.event.domain.LinkScope;

/**
 * An own event as its owner and the moderators see it: every status, the
 * reason it was taken down, and what the edit form needs to fill itself.
 */
public record OwnEventView(String slug, EventStatus status, String suspensionReason, String locale, String name,
		String placeName, String description, ActivityType activityType, Difficulty difficulty, Instant startsAt,
		Instant endsAt, Instant passesAt, double latitude, double longitude, BigDecimal distanceKm,
		Integer elevationGainM, Integer maxParticipants, List<String> eventLinks, List<String> communityLinks,
		Instant publishedAt, Instant updatedAt, String communitySlug, String organizer) {

	static OwnEventView of(Event event) {
		EventTranslation text = event.translation(event.getOriginalLocale());
		return new OwnEventView(event.getSlug(), event.getStatus(), event.getSuspensionReason(),
				event.getOriginalLocale(), text.getName(), text.getPlaceName(), text.getDescription(),
				event.getActivityType(), event.getDifficulty(), event.getStartsAt(), event.getEndsAt(),
				event.getPassesAt(), event.getStartPoint().getY(), event.getStartPoint().getX(), event.getDistanceKm(),
				event.getElevationGainM(), event.getVisibleCapacity(), urls(event, LinkScope.EVENT),
				urls(event, LinkScope.COMMUNITY), event.getPublishedAt(), event.getUpdatedAt(),
				event.getCommunitySlug(), event.getOrganizer());
	}

	private static List<String> urls(Event event, LinkScope scope) {
		return event.links(scope).stream().map(EventLink::getUrl).toList();
	}

}
