package app.wildro.event.own;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import app.wildro.event.domain.Event;

/**
 * The questions only own events raise: whose, and how many lately. The limits
 * count per account (SPEC.md §18.2), which is the author: for a personal event
 * also its owner. Writes go through the domain repository.
 */
interface OwnEventRepository extends Repository<Event, Long> {

	@Query("""
			select count(e) from Event e
			where e.authorUserId = :author and e.publishedAt > :since
			""")
	long countPublishedSince(@Param("author") String author, @Param("since") Instant since);

	/** Drafts and published events still ahead: the "30 at once" of SPEC.md §18.2. */
	@Query("""
			select count(e) from Event e
			where e.authorUserId = :author and e.passesAt > :now
			  and e.status in (app.wildro.event.domain.EventStatus.DRAFT, app.wildro.event.domain.EventStatus.PUBLISHED)
			""")
	long countUpcoming(@Param("author") String author, @Param("now") Instant now);

	/** What a person wrote or owns: their personal events and those they wrote for a community. */
	@EntityGraph(attributePaths = "translations")
	@Query("""
			select e from Event e
			where e.authorUserId = :user or e.ownerUserId = :user
			order by e.startsAt desc
			limit 100
			""")
	List<Event> mine(@Param("user") String user);

	/** Everything a community owns, drafts and the cancelled included: its admins' working list. */
	@EntityGraph(attributePaths = "translations")
	@Query("""
			select e from Event e
			where e.ownerCommunityId = :community
			order by e.startsAt desc
			limit 200
			""")
	List<Event> ofCommunity(@Param("community") long community);

}
