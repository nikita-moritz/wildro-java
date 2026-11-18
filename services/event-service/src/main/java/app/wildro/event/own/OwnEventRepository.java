package app.wildro.event.own;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import app.wildro.event.domain.Event;

/** The questions only own events raise: whose, and how many lately. Writes go through the domain repository. */
interface OwnEventRepository extends Repository<Event, Long> {

	@Query("""
			select count(e) from Event e
			where e.ownerUserId = :owner and e.publishedAt > :since
			""")
	long countPublishedSince(@Param("owner") String owner, @Param("since") Instant since);

	/** Drafts and published events still ahead: the "30 at once" of SPEC.md §18.2. */
	@Query("""
			select count(e) from Event e
			where e.ownerUserId = :owner and e.passesAt > :now
			  and e.status in (app.wildro.event.domain.EventStatus.DRAFT, app.wildro.event.domain.EventStatus.PUBLISHED)
			""")
	long countUpcoming(@Param("owner") String owner, @Param("now") Instant now);

	@EntityGraph(attributePaths = "translations")
	List<Event> findFirst100ByOwnerUserIdOrderByStartsAtDesc(String owner);

}
