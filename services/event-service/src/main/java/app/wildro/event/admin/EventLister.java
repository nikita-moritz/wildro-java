package app.wildro.event.admin;

import app.wildro.event.domain.EventStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;

/**
 * The admin's view of what is there (SPEC.md §0, open question 4). By default
 * what has not passed yet, drafts included, because re-checking sources is work
 * on those; asked for everything, past and cancelled events join them.
 * <p>
 * Paginated like every list, admin ones included.
 */
@Service
public class EventLister {

	public static final int MAX_PAGE_SIZE = 100;

	/** Names are shown in German, the primary locale; the admin interface language is not decided yet. */
	static final String NAME_LOCALE = "de";

	private final EventListingRepository events;

	private final Clock clock;

	EventLister(EventListingRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Slice<EventSummary> list(boolean all, int page, int size) {
		if (page < 0) {
			throw new IllegalArgumentException("page starts at 0");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
		}
		PageRequest request = PageRequest.of(page, size);
		if (all) {
			return this.events.findAllSummaries(NAME_LOCALE, request);
		}
		return this.events.findSummariesNotPassed(EnumSet.of(EventStatus.DRAFT, EventStatus.PUBLISHED),
				this.clock.instant(), NAME_LOCALE, request);
	}

}
