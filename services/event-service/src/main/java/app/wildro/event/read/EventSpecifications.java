package app.wildro.event.read;

import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.GeoPoints;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The predicates of the public lists, one per filter, composed at runtime.
 * <p>
 * Proximity and the map's viewport are PostGIS functions called through the
 * criteria builder: neither exists in the standard Criteria API (AGENTS.md, the
 * five traps). Both take the {@code geography} column directly so the GiST
 * index answers them and distances are metres on the sphere, not degrees.
 */
final class EventSpecifications {

	private static final int METRES_PER_KM = 1000;

	private EventSpecifications() {
	}

	/** Published and not passed yet: what the public may be offered. */
	static Specification<Event> upcomingPublished(Instant now) {
		return (root, query, cb) -> cb.and(
				cb.equal(root.get("status"), EventStatus.PUBLISHED),
				cb.greaterThan(root.get("passesAt"), now));
	}

	static Specification<Event> matching(FeedFilter filter) {
		Specification<Event> spec = Specification.unrestricted();
		if (!filter.types().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("activityType").in(filter.types()));
		}
		if (!filter.difficulties().isEmpty()) {
			spec = spec.and((root, query, cb) -> root.get("difficulty").in(filter.difficulties()));
		}
		if (filter.from() != null) {
			Instant start = startOfDay(filter.from());
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startsAt"), start));
		}
		if (filter.to() != null) {
			Instant end = startOfDay(filter.to().plusDays(1));
			spec = spec.and((root, query, cb) -> cb.lessThan(root.get("startsAt"), end));
		}
		if (filter.minKm() != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("distanceKm"), filter.minKm()));
		}
		if (filter.maxKm() != null) {
			spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("distanceKm"), filter.maxKm()));
		}
		if (filter.cost() == FeedFilter.Cost.FREE) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("price").get("amount"), BigDecimal.ZERO));
		}
		if (filter.cost() == FeedFilter.Cost.PAID) {
			spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("price").get("amount"), BigDecimal.ZERO));
		}
		if (filter.near() != null) {
			spec = spec.and(within(filter.near()));
		}
		for (String word : filter.words()) {
			spec = spec.and(containing(word));
		}
		return spec;
	}

	/**
	 * How close a misspelt word must be to a word of the event to count, on
	 * pg_trgm's 0 to 1 scale. 0.5 lets "monschou" find Monschau and keeps
	 * "wald" from matching every "wanderung".
	 */
	static final double TYPO_SIMILARITY = 0.5;

	/**
	 * One folded word of the query: found as part of the stored search text, or,
	 * for a typo, close enough to one of its words. Both run on the trigram
	 * index of V7. The word is folded to letters, digits and spaces by
	 * SearchText, so it carries no LIKE wildcard to escape.
	 */
	static Specification<Event> containing(String word) {
		return (root, query, cb) -> cb.or(
				cb.like(root.get("searchText"), "%" + word + "%"),
				cb.greaterThanOrEqualTo(
						cb.function("word_similarity", Double.class, cb.literal(word), root.get("searchText")),
						TYPO_SIMILARITY));
	}

	/** Within the radius of a point, on the sphere, through the spatial index. */
	static Specification<Event> within(FeedFilter.Near near) {
		return (root, query, cb) -> cb.isTrue(cb.function("st_dwithin", Boolean.class,
				root.get("startPoint"),
				cb.literal(GeoPoints.of(near.latitude(), near.longitude())),
				cb.literal((double) near.radiusKm() * METRES_PER_KM)));
	}

	/** Inside the visible rectangle of the map: the map never asks for the whole country at once. */
	static Specification<Event> inside(Viewport viewport) {
		return (root, query, cb) -> cb.isTrue(cb.function("st_intersects", Boolean.class,
				root.get("startPoint"),
				cb.function("st_makeenvelope", Object.class,
						cb.literal(viewport.west()), cb.literal(viewport.south()),
						cb.literal(viewport.east()), cb.literal(viewport.north()),
						cb.literal(GeoPoints.WGS84))));
	}

	/** Midnight in the product's zone, which is where a visitor's "from 3 October" starts. */
	private static Instant startOfDay(LocalDate day) {
		return day.atStartOfDay(Event.ZONE).toInstant();
	}

}
