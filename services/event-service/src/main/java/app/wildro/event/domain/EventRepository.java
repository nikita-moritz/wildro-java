package app.wildro.event.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Storage for events: the writes and the lookups every slice shares.
 * <p>
 * Read shapes live in the slice that reads them ({@code read/}, {@code admin/}),
 * each with its own repository over the same entity. The domain does not know
 * who reads it or how, so a new read form never touches this package.
 * <p>
 * {@link JpaSpecificationExecutor} is here from the start because the public
 * lists will filter by activity type, difficulty, date and proximity in any
 * combination: a repository method per combination does not scale past the
 * first two filters.
 */
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

	Optional<Event> findBySlug(String slug);

	boolean existsBySlug(String slug);

	/**
	 * The events of a batch, with their translations, in one query. Looking them up
	 * one at a time and letting the translations load lazily would cost two
	 * queries per event, four hundred for a full batch. No paging here, so fetching
	 * the collection is safe.
	 */
	@EntityGraph(attributePaths = "translations")
	List<Event> findBySlugIn(Collection<String> slugs);

}
