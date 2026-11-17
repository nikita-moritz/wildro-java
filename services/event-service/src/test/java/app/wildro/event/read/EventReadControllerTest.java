package app.wildro.event.read;

import app.wildro.event.TestcontainersConfiguration;
import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.EventTranslation;
import app.wildro.event.domain.GeoPoints;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public read path, through HTTP. What matters here is what the public can
 * and cannot see, so each test is one visibility rule.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventReadControllerTest {

	private static final Instant NOW = Instant.now();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private EventRepository events;

	private Event save(String slug, EventStatus status, Instant startsAt) {
		return save(slug, status, startsAt, null);
	}

	private Event save(String slug, EventStatus status, Instant startsAt, Instant endsAt) {
		return this.events.saveAndFlush(Event.builder()
				.slug(slug)
				.status(status)
				.activityType(ActivityType.HIKING)
				.startsAt(startsAt)
				.endsAt(endsAt)
				.startPoint(GeoPoints.of(50.7753, 6.0839))
				.organizer("Wandertreff Aachen")
				.source("https://example.org/" + slug, NOW)
				.maxParticipants(12)
				.translation("de", new EventTranslation("Wanderung " + slug, "Aachen, Deutschland", "Beschreibung."))
				.translation("en", new EventTranslation("Hike " + slug, "Aachen, Germany", "Description."))
				.translation("ru", new EventTranslation("Поход " + slug, "Ахен, Германия", "Описание."))
				.build());
	}

	private static Instant inDays(int days) {
		return NOW.plus(Duration.ofDays(days));
	}

	@Test
	void theFeedShowsOnlyWhatIsPublishedAndAheadSoonestFirst() throws Exception {
		save("later", EventStatus.PUBLISHED, inDays(20));
		save("sooner", EventStatus.PUBLISHED, inDays(5));
		save("draft", EventStatus.DRAFT, inDays(6));
		save("past", EventStatus.PUBLISHED, inDays(-10));
		Event cancelled = save("cancelled", EventStatus.PUBLISHED, inDays(7));
		cancelled.cancel();
		this.events.flush();

		this.mvc.perform(get("/v1/events").param("locale", "de"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].slug", contains("sooner", "later")))
				.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void theFeedIsInTheRequestedLanguage() throws Exception {
		save("language", EventStatus.PUBLISHED, inDays(3));

		this.mvc.perform(get("/v1/events").param("locale", "ru"))
				.andExpect(jsonPath("$.items[0].name").value("Поход language"))
				.andExpect(jsonPath("$.items[0].placeName").value("Ахен, Германия"));
	}

	@Test
	void theFeedIsPaged() throws Exception {
		for (int i = 1; i <= 3; i++) {
			save("paged-" + i, EventStatus.PUBLISHED, inDays(i));
		}

		this.mvc.perform(get("/v1/events").param("locale", "en").param("size", "2"))
				.andExpect(jsonPath("$.items[*].slug", contains("paged-1", "paged-2")))
				.andExpect(jsonPath("$.hasNext").value(true));
		this.mvc.perform(get("/v1/events").param("locale", "en").param("size", "2").param("page", "1"))
				.andExpect(jsonPath("$.items[*].slug", contains("paged-3")))
				.andExpect(jsonPath("$.hasNext").value(false));
	}

	/**
	 * Saved latest first, and the soonest one lasts longest, so neither the order
	 * of the rows nor the passes_at index puts it first: only sorting by start
	 * does. A page cut without that sort once left the feed out of date order.
	 */
	@Test
	void eachPageTakesTheSoonestEventsNotTheFirstSaved() throws Exception {
		save("third", EventStatus.PUBLISHED, inDays(30));
		save("second", EventStatus.PUBLISHED, inDays(20));
		save("first", EventStatus.PUBLISHED, inDays(10), inDays(40));

		this.mvc.perform(get("/v1/events").param("locale", "en").param("size", "2"))
				.andExpect(jsonPath("$.items[*].slug", contains("first", "second")));
		this.mvc.perform(get("/v1/events").param("locale", "en").param("size", "2").param("page", "1"))
				.andExpect(jsonPath("$.items[*].slug", contains("third")));
	}

	@Test
	void theEventPageCarriesThePriceOnlyWhenTheSourceNamedOne() throws Exception {
		Event priced = save("priced", EventStatus.PUBLISHED, inDays(4));
		priced.revise(Event.builder()
				.slug("priced").status(EventStatus.PUBLISHED).activityType(ActivityType.HIKING)
				.startsAt(priced.getStartsAt()).startPoint(GeoPoints.of(50.7753, 6.0839))
				.organizer("Wandertreff Aachen").source("https://example.org/priced", NOW).maxParticipants(12)
				.price(new app.wildro.event.domain.Price(new java.math.BigDecimal("2.5"), "EUR"))
				.translation("de", new EventTranslation("Wanderung priced", "Aachen, Deutschland", "Beschreibung."))
				.translation("en", new EventTranslation("Hike priced", "Aachen, Germany", "Description."))
				.translation("ru", new EventTranslation("Поход priced", "Ахен, Германия", "Описание."))
				.build());
		this.events.flush();
		save("unpriced", EventStatus.PUBLISHED, inDays(4));

		this.mvc.perform(get("/v1/events/priced").param("locale", "en"))
				.andExpect(jsonPath("$.price.amount").value(2.5))
				.andExpect(jsonPath("$.price.currency").value("EUR"));
		this.mvc.perform(get("/v1/events/unpriced").param("locale", "en"))
				.andExpect(jsonPath("$.price").value(org.hamcrest.Matchers.nullValue()));
	}

	/** Unbounded depth would mint unbounded addresses for a crawler to walk. */
	@Test
	void theFeedDoesNotGoArbitrarilyDeep() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("page", "100"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").exists());
	}

	@Test
	void anUnknownLanguageIsABadRequest() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "fr")).andExpect(status().isBadRequest());
	}

	@Test
	void theEventPageCarriesWhatThePageShows() throws Exception {
		save("page", EventStatus.PUBLISHED, inDays(4));

		this.mvc.perform(get("/v1/events/page").param("locale", "en"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Hike page"))
				.andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.latitude").value(50.7753))
				.andExpect(jsonPath("$.longitude").value(6.0839))
				.andExpect(jsonPath("$.sourceUrl").value("https://example.org/page"))
				.andExpect(jsonPath("$.startsAt").isString())
				.andExpect(jsonPath("$.passesAt").isString());
	}

	/** Both lists are always there, so the page never has to tell "none" from "not sent". */
	@Test
	void theEventPageAlwaysCarriesBothListsOfLinks() throws Exception {
		save("no-links", EventStatus.PUBLISHED, inDays(4));

		this.mvc.perform(get("/v1/events/no-links").param("locale", "de"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventLinks").isEmpty())
				.andExpect(jsonPath("$.communityLinks").isEmpty());
	}

	/** Every stage-one event is external, and an external event never shows a capacity. */
	@Test
	void theEventPageNeverCarriesACapacity() throws Exception {
		save("no-capacity", EventStatus.PUBLISHED, inDays(4));

		this.mvc.perform(get("/v1/events/no-capacity").param("locale", "de"))
				.andExpect(jsonPath("$", not(hasKey("maxParticipants"))))
				.andExpect(jsonPath("$", not(hasKey("visibleCapacity"))));
	}

	@Test
	void aDraftIsNotThere() throws Exception {
		save("secret-draft", EventStatus.DRAFT, inDays(4));

		this.mvc.perform(get("/v1/events/secret-draft").param("locale", "de"))
				.andExpect(status().isNotFound());
	}

	/** A cancelled or past page keeps its address for a year (SPEC.md §0.3). */
	@Test
	void cancelledAndPastEventsStayReachableByTheirAddress() throws Exception {
		Event cancelled = save("still-here", EventStatus.PUBLISHED, inDays(4));
		cancelled.cancel();
		save("long-ago", EventStatus.PUBLISHED, inDays(-30));
		this.events.flush();

		this.mvc.perform(get("/v1/events/still-here").param("locale", "de"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		this.mvc.perform(get("/v1/events/long-ago").param("locale", "de"))
				.andExpect(status().isOk());
	}

	@Test
	void anUnknownSlugIsNotFound() throws Exception {
		this.mvc.perform(get("/v1/events/nothing-here").param("locale", "de"))
				.andExpect(status().isNotFound());
	}

}
