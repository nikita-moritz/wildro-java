package app.wildro.event.read;

import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Event;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The public read path's queries. Read-only by construction: it extends
 * {@link Repository}, so nothing reachable from a public request can save or
 * delete (SPEC.md §0.1, no network write path on stage one).
 * <p>
 * Lists are read in two steps. A Specification selects one page of ids, then
 * the queries here turn those ids into rows in one language. Doing it in one
 * query would join every translation, which triples the rows and corrupts page
 * boundaries (AGENTS.md, the five traps); the locale predicate here keeps it at
 * one row per event.
 */
interface EventReadRepository extends Repository<Event, Long> {

	/** Cards for the given ids, in the feed's order, which is also the order the ids were selected in. */
	@Query("""
			select new app.wildro.event.read.EventCard(
				e.slug, t.name, t.placeName, e.activityType, e.difficulty, e.startsAt, e.endsAt,
				e.organizer, e.distanceKm, e.elevationGainM, e.coverImageUrl,
				concat(e.coverCredit.author, ', ', e.coverCredit.license),
				e.price.amount, e.price.currency)
			from Event e join e.translations t
			where key(t) = :locale and e.id in :ids
			order by e.startsAt asc, e.id asc
			""")
	List<EventCard> findCards(@Param("ids") Collection<Long> ids, @Param("locale") String locale);

	/** Marker data for the given ids. The point comes back as JTS and is split into degrees by the caller. */
	@Query("""
			select new app.wildro.event.read.EventReadRepository$PointRow(
				e.slug, t.name, t.placeName, e.activityType, e.startsAt, e.endsAt, e.coverImageUrl,
				concat(e.coverCredit.author, ', ', e.coverCredit.license),
				e.price.amount, e.price.currency, e.startPoint)
			from Event e join e.translations t
			where key(t) = :locale and e.id in :ids
			order by e.startsAt asc, e.id asc
			""")
	List<PointRow> findPointRows(@Param("ids") Collection<Long> ids, @Param("locale") String locale);

	/** One event with its translations. One row, so fetching the collection is safe. */
	@EntityGraph(attributePaths = "translations")
	Optional<Event> findWithTranslationsBySlug(String slug);

	record PointRow(String slug, String name, String placeName, ActivityType activityType, Instant startsAt,
			Instant endsAt, String coverImageUrl, String coverCredit, BigDecimal priceAmount, String priceCurrency,
			Point point) {
	}

	/** What the Specification step selects: ids only, so no entity is loaded to find a page. */
	interface IdView {

		Long getId();

	}

}
