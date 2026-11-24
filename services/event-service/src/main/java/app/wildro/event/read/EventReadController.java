package app.wildro.event.read;

import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Difficulty;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.SearchText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Public, read-only, unauthenticated: a visitor sees events without signing in
 * (SPEC.md §0.1). Versioned from the first endpoint, because adding a v2 later is
 * cheaper than breaking v1 under a frontend that is already cached.
 * <p>
 * The caller is the frontend's server, which caches what it renders; no public
 * page calls this in its hot path (AGENTS.md, product goal). Since 2026-09-29
 * the service listens on loopback only and the frontend is its sole client.
 */
@RestController
@RequestMapping("/v1/events")
class EventReadController {

	/**
	 * How deep the feed goes. OFFSET gets slower with depth, and unbounded depth
	 * mints unbounded addresses for a crawler to walk (AGENTS.md, the five traps).
	 */
	static final int MAX_PAGE = 99;

	static final int MAX_SIZE = 50;

	private final EventReader reader;

	private final Clock clock;

	private final String siteUrl;

	EventReadController(EventReader reader, Clock clock, @Value("${wildro.site-url}") String siteUrl) {
		this.reader = reader;
		this.clock = clock;
		this.siteUrl = siteUrl.replaceAll("/+$", "");
	}

	/**
	 * Upcoming published events, soonest first, narrowed by any combination of
	 * the optional filters: {@code type} and {@code difficulty} (repeatable),
	 * {@code from} and {@code to} (days), {@code minKm} and {@code maxKm}, and
	 * {@code lat}, {@code lon} with {@code radiusKm} for "near me", {@code q},
	 * a text query over name, organiser and place in every language, and
	 * {@code community} (repeatable), the ids of the groups whose events to keep.
	 */
	@GetMapping
	FeedPage feed(@RequestParam String locale, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "12") int size, @RequestParam(name = "type", required = false) List<String> types,
			@RequestParam(name = "difficulty", required = false) List<String> difficulties,
			@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) BigDecimal minKm, @RequestParam(required = false) BigDecimal maxKm,
			@RequestParam(required = false) Double lat, @RequestParam(required = false) Double lon,
			@RequestParam(required = false) Integer radiusKm, @RequestParam(required = false) String price,
			@RequestParam(required = false) String q,
			@RequestParam(name = "community", required = false) List<Long> communities) {
		requireLocale(locale);
		if (page < 0 || page > MAX_PAGE) {
			throw badRequest("page must be between 0 and " + MAX_PAGE);
		}
		if (size < 1 || size > MAX_SIZE) {
			throw badRequest("size must be between 1 and " + MAX_SIZE);
		}
		return this.reader.feed(locale, page, size,
				filter(types, difficulties, from, to, minKm, maxKm, lat, lon, radiusKm, price, q, communities));
	}

	/**
	 * Markers inside the visible rectangle, with the same optional filters as the
	 * feed. At most {@link EventReader#MAX_POINTS}; {@code truncated} says there
	 * were more.
	 */
	@GetMapping("/points")
	PointsPage points(@RequestParam String locale, @RequestParam double south, @RequestParam double west,
			@RequestParam double north, @RequestParam double east,
			@RequestParam(name = "type", required = false) List<String> types,
			@RequestParam(name = "difficulty", required = false) List<String> difficulties,
			@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) BigDecimal minKm, @RequestParam(required = false) BigDecimal maxKm,
			@RequestParam(required = false) String price, @RequestParam(required = false) String q,
			@RequestParam(name = "community", required = false) List<Long> communities) {
		requireLocale(locale);
		Viewport viewport;
		try {
			viewport = new Viewport(south, west, north, east);
		}
		catch (IllegalArgumentException ex) {
			throw badRequest(ex.getMessage());
		}
		return this.reader.points(locale, viewport,
				filter(types, difficulties, from, to, minKm, maxKm, null, null, null, price, q, communities));
	}

	/** A community's upcoming events, for its page. */
	@GetMapping("/community/{communityId}")
	List<EventCard> ofCommunity(@PathVariable long communityId, @RequestParam String locale) {
		requireLocale(locale);
		return this.reader.ofCommunity(communityId, locale);
	}

	/**
	 * A community's events as one calendar to subscribe to (RFC 5545). The name
	 * is the group's, given by the caller: this service knows the group by its
	 * id only. Whether the group may be shown at all is the caller's check, as
	 * for the list above.
	 */
	@GetMapping(path = "/community/{communityId}/calendar.ics", produces = "text/calendar;charset=UTF-8")
	String communityCalendar(@PathVariable long communityId, @RequestParam String locale,
			@RequestParam(required = false) String name) {
		requireLocale(locale);
		String shown = name == null ? null : name.strip().substring(0, Math.min(name.strip().length(), 120));
		return CalendarFile.feed(shown, this.reader.calendarOfCommunity(communityId, locale),
				(event) -> this.siteUrl + "/" + locale + "/event/" + event.slug(), this.clock.instant());
	}

	/** One event, published or cancelled. A draft answers 404, as if it were not there. */
	@GetMapping("/{slug}")
	EventPage page(@PathVariable String slug, @RequestParam String locale) {
		requireLocale(locale);
		return this.reader.page(slug, locale)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no event " + slug));
	}

	/**
	 * The event as a calendar file (RFC 5545), for "add to calendar". Same
	 * visibility as the page: published or cancelled, a draft is not there.
	 */
	@GetMapping(path = "/{slug}/calendar.ics", produces = "text/calendar;charset=UTF-8")
	String calendar(@PathVariable String slug, @RequestParam String locale) {
		requireLocale(locale);
		EventPage event = this.reader.page(slug, locale)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no event " + slug));
		return CalendarFile.of(event, this.siteUrl + "/" + locale + "/event/" + slug, this.clock.instant());
	}

	private static FeedFilter filter(List<String> types, List<String> difficulties, String from, String to,
			BigDecimal minKm, BigDecimal maxKm, Double lat, Double lon, Integer radiusKm, String price, String q,
			List<Long> communities) {
		try {
			FeedFilter.Near near = null;
			if (lat != null || lon != null || radiusKm != null) {
				if (lat == null || lon == null || radiusKm == null) {
					throw new IllegalArgumentException("lat, lon and radiusKm go together");
				}
				near = new FeedFilter.Near(lat, lon, radiusKm);
			}
			if ((minKm != null && minKm.signum() < 0) || (maxKm != null && maxKm.signum() < 0)) {
				throw new IllegalArgumentException("distances must not be negative");
			}
			return new FeedFilter(enums(types, ActivityType.class), enums(difficulties, Difficulty.class),
					day(from, "from"), day(to, "to"), minKm, maxKm, near, cost(price), SearchText.words(q),
					communities == null ? Set.of() : new HashSet<>(communities));
		}
		catch (IllegalArgumentException ex) {
			throw badRequest(ex.getMessage());
		}
	}

	/** {@code free} or {@code paid}; anything else is a bad request, absent means either. */
	private static FeedFilter.Cost cost(String price) {
		if (price == null || price.isBlank()) {
			return null;
		}
		return switch (price) {
			case "free" -> FeedFilter.Cost.FREE;
			case "paid" -> FeedFilter.Cost.PAID;
			default -> throw new IllegalArgumentException("price must be free or paid");
		};
	}

	private static <E extends Enum<E>> Set<E> enums(List<String> values, Class<E> type) {
		Set<E> result = EnumSet.noneOf(type);
		if (values != null) {
			for (String value : values) {
				try {
					result.add(Enum.valueOf(type, value));
				}
				catch (IllegalArgumentException ex) {
					throw new IllegalArgumentException("unknown " + type.getSimpleName() + ": " + value);
				}
			}
		}
		return result;
	}

	private static LocalDate day(String value, String name) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException ex) {
			throw new IllegalArgumentException(name + " must be a date like 2026-10-03");
		}
	}

	private static void requireLocale(String locale) {
		if (!Event.LOCALES.contains(locale)) {
			throw badRequest("locale must be one of " + Event.LOCALES);
		}
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

}
