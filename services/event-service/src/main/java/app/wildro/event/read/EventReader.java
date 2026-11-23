package app.wildro.event.read;

import app.wildro.event.domain.CoverCredit;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.EventTranslation;
import app.wildro.event.domain.LinkScope;
import app.wildro.event.read.EventReadRepository.IdView;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The public read path: the feed, the map and the page of one event.
 * <p>
 * What the public may see is decided here once. The feed and the map carry only
 * what is published and still ahead. A single page is reachable while published
 * or cancelled, past or not, because a cancelled or past page keeps its address
 * for a year (SPEC.md §0.3); a draft is not public and a suspended event was
 * taken down, so neither exists here.
 * <p>
 * The Specification step runs through the domain repository, read-only. The
 * public slice's own repository deliberately does not extend
 * JpaSpecificationExecutor: since Spring Data 4 that interface also offers
 * update and delete by specification.
 */
@Service
public class EventReader {

	/** Soonest first; the id breaks ties so an event cannot move between pages. */
	static final Sort FEED_ORDER = Sort.by("startsAt").ascending().and(Sort.by("id").ascending());

	/** A viewport's worth of markers. More than this and the visitor is asked to zoom in. */
	static final int MAX_POINTS = 300;

	/** A community page lists this many upcoming events; more is a feed, which has its own page. */
	static final int MAX_COMMUNITY_EVENTS = 50;

	/** A group's calendar: enough for a very busy year, small enough for any calendar app. */
	static final int MAX_CALENDAR_EVENTS = 200;

	static final Duration CALENDAR_KEEPS_PAST = Duration.ofDays(60);

	private final EventRepository events;

	private final EventReadRepository read;

	private final Clock clock;

	EventReader(EventRepository events, EventReadRepository read, Clock clock) {
		this.events = events;
		this.read = read;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public FeedPage feed(String locale, int page, int size, FeedFilter filter) {
		Specification<Event> spec = EventSpecifications.upcomingPublished(this.clock.instant())
				.and(EventSpecifications.matching(filter));
		// The order travels in the Pageable: slice() takes the Pageable's sort and
		// drops one set by sortBy(), which left the pages in id order.
		Slice<IdView> ids = this.events.findBy(spec,
				query -> query.as(IdView.class).slice(PageRequest.of(page, size, FEED_ORDER)));
		List<Long> idList = ids.getContent().stream().map(IdView::getId).toList();
		List<EventCard> cards = idList.isEmpty() ? List.of() : this.read.findCards(idList, locale);
		return new FeedPage(cards, page, size, ids.hasNext());
	}

	@Transactional(readOnly = true)
	public PointsPage points(String locale, Viewport viewport, FeedFilter filter) {
		Specification<Event> spec = EventSpecifications.upcomingPublished(this.clock.instant())
				.and(EventSpecifications.inside(viewport))
				.and(EventSpecifications.matching(filter));
		List<IdView> ids = this.events.findBy(spec,
				query -> query.as(IdView.class).sortBy(FEED_ORDER).limit(MAX_POINTS + 1).all());
		boolean truncated = ids.size() > MAX_POINTS;
		List<Long> idList = ids.stream().limit(MAX_POINTS).map(IdView::getId).toList();
		List<EventPoint> points = idList.isEmpty() ? List.of()
				: this.read.findPointRows(idList, locale).stream()
						.map(row -> new EventPoint(row.slug(), row.name(), row.placeName(), row.activityType(),
								row.startsAt(), row.endsAt(), row.coverImageUrl(), row.coverCredit(), row.priceAmount(),
								row.priceCurrency(), row.point().getY(), row.point().getX()))
						.toList();
		return new PointsPage(points, truncated);
	}

	/** A community's upcoming published events, soonest first, for its page (SPEC.md §18.3). */
	@Transactional(readOnly = true)
	public List<EventCard> ofCommunity(long communityId, String locale) {
		Specification<Event> spec = EventSpecifications.upcomingPublished(this.clock.instant())
				.and((root, query, cb) -> cb.equal(root.get("ownerCommunityId"), communityId));
		List<Long> ids = this.events.findBy(spec,
				query -> query.as(IdView.class).sortBy(FEED_ORDER).limit(MAX_COMMUNITY_EVENTS).all())
			.stream().map(IdView::getId).toList();
		return ids.isEmpty() ? List.of() : this.read.findCards(ids, locale);
	}

	/**
	 * A community's events for a calendar subscription: what is ahead, plus what
	 * passed in the last weeks so a subscribed calendar does not lose an outing
	 * the day after. Cancelled ones stay in, so the calendar shows them cancelled
	 * instead of silently dropping them; drafts and suspended events never appear.
	 */
	@Transactional(readOnly = true)
	public List<EventPage> calendarOfCommunity(long communityId, String locale) {
		Instant since = this.clock.instant().minus(CALENDAR_KEEPS_PAST);
		Specification<Event> spec = (root, query, cb) -> cb.and(
				cb.equal(root.get("ownerCommunityId"), communityId),
				root.get("status").in(EventStatus.PUBLISHED, EventStatus.CANCELLED),
				cb.greaterThan(root.get("passesAt"), since));
		List<Long> ids = this.events.findBy(spec,
				query -> query.as(IdView.class).sortBy(FEED_ORDER).limit(MAX_CALENDAR_EVENTS).all())
			.stream().map(IdView::getId).toList();
		return ids.isEmpty() ? List.of()
				: this.read.findWithTranslationsByIdIn(ids).stream()
					.map(event -> toPage(event, locale))
					.sorted(Comparator.comparing(EventPage::startsAt).thenComparing(EventPage::id))
					.toList();
	}

	@Transactional(readOnly = true)
	public Optional<EventPage> page(String slug, String locale) {
		return this.read.findWithTranslationsBySlug(slug)
				// Published or cancelled: a draft is not public yet, a suspended
				// event was taken off the site by moderation (SPEC.md §18.2).
				.filter(event -> event.getStatus() == EventStatus.PUBLISHED || event.getStatus() == EventStatus.CANCELLED)
				.map(event -> toPage(event, locale));
	}

	private static EventPage toPage(Event event, String locale) {
		EventTranslation text = event.translation(locale);
		return new EventPage(
				event.getId(),
				event.getSlug(),
				event.getStatus(),
				text.getName(),
				text.getPlaceName(),
				text.getDescription(),
				event.getActivityType(),
				event.getDifficulty(),
				event.getStartsAt(),
				event.getEndsAt(),
				event.getPassesAt(),
				event.getStartPoint().getY(),
				event.getStartPoint().getX(),
				event.getShownOrganizer(),
				event.getSourceUrl(),
				event.getSourceCheckedAt(),
				event.getDistanceKm(),
				event.getElevationGainM(),
				event.getCoverImageUrl(),
				credit(event.getCoverCredit()),
				event.getPrice() == null ? null
						: new EventPage.Price(event.getPrice().getAmount(), event.getPrice().getCurrency()),
				links(event, LinkScope.EVENT),
				links(event, LinkScope.COMMUNITY),
				event.getKind(),
				event.getOriginalLocale(),
				event.getCommunitySlug(),
				event.getUpdatedAt());
	}

	private static EventPage.Credit credit(CoverCredit credit) {
		return credit == null ? null
				: new EventPage.Credit(credit.getAuthor(), credit.getLicense(), credit.getLicenseUrl(), credit.getSourceUrl());
	}

	private static List<EventPage.Link> links(Event event, LinkScope scope) {
		return event.links(scope).stream().map(link -> new EventPage.Link(link.getNetwork(), link.getUrl())).toList();
	}

}
