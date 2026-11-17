package app.wildro.event.admin;

import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

/**
 * The admin list's queries. Read-only by construction: it extends
 * {@link Repository}, not {@code JpaRepository}, so it offers no save or delete.
 * <p>
 * Lists return {@link Slice}, not {@code Page}: a page runs a count query on
 * every request, and nobody reads the total.
 */
interface EventListingRepository extends Repository<Event, Long> {

	/**
	 * Events in the given states that have not passed yet, soonest first. The id
	 * breaks ties so that two events starting at the same minute cannot swap
	 * places between two pages and show up twice or not at all.
	 */
	@Query("""
			select new app.wildro.event.admin.EventSummary(
				e.slug, e.status, e.activityType, e.startsAt, e.passesAt, t.name)
			from Event e join e.translations t
			where key(t) = :locale
			  and e.status in :statuses
			  and e.passesAt > :now
			order by e.startsAt asc, e.id asc
			""")
	Slice<EventSummary> findSummariesNotPassed(
			@Param("statuses") Collection<EventStatus> statuses,
			@Param("now") Instant now,
			@Param("locale") String locale,
			Pageable pageable);

	/** Every event, whatever its state or date. */
	@Query("""
			select new app.wildro.event.admin.EventSummary(
				e.slug, e.status, e.activityType, e.startsAt, e.passesAt, t.name)
			from Event e join e.translations t
			where key(t) = :locale
			order by e.startsAt asc, e.id asc
			""")
	Slice<EventSummary> findAllSummaries(@Param("locale") String locale, Pageable pageable);

}
