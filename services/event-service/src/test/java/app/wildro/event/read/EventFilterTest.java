package app.wildro.event.read;

import app.wildro.event.TestcontainersConfiguration;
import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.Difficulty;
import app.wildro.event.domain.Event;
import app.wildro.event.domain.EventRepository;
import app.wildro.event.domain.EventStatus;
import app.wildro.event.domain.EventTranslation;
import app.wildro.event.domain.GeoPoints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The filters of the feed and the map's viewport, against a real PostGIS. The
 * geography tests are the ones that matter most: a radius read in degrees
 * instead of metres would still return rows, just the wrong ones.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventFilterTest {

	private static final LocalDate DAY = LocalDate.now(Event.ZONE).plusDays(30);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private EventRepository events;

	private void save(String slug, ActivityType type, Difficulty difficulty, int dayOffset, double lat, double lon,
			String km) {
		save(slug, type, difficulty, dayOffset, lat, lon, km, null);
	}

	private void save(String slug, ActivityType type, Difficulty difficulty, int dayOffset, double lat, double lon,
			String km, String price) {
		this.events.save(Event.builder()
				.slug(slug)
				.status(EventStatus.PUBLISHED)
				.activityType(type)
				.difficulty(difficulty)
				.startsAt(LocalDateTime.of(DAY.plusDays(dayOffset), java.time.LocalTime.of(10, 0)).atZone(Event.ZONE).toInstant())
				.startPoint(GeoPoints.of(lat, lon))
				.organizer("Testgruppe")
				.source("https://example.org/" + slug, null)
				.distanceKm(km == null ? null : new BigDecimal(km))
				.price(price == null ? null : new app.wildro.event.domain.Price(new BigDecimal(price), "EUR"))
				.translation("de", new EventTranslation("DE " + slug, "Ort", "Text."))
				.translation("en", new EventTranslation("EN " + slug, "Place", "Text."))
				.translation("ru", new EventTranslation("RU " + slug, "Место", "Текст."))
				.build());
	}

	@BeforeEach
	void seed() {
		save("aachen-hike", ActivityType.HIKING, Difficulty.EASY, 0, 50.7753, 6.0839, "11", "0");
		save("cologne-ride", ActivityType.CYCLING, Difficulty.MEDIUM, 1, 50.9413, 6.9583, "45", "12.50");
		save("berlin-hike", ActivityType.HIKING, Difficulty.HARD, 2, 52.5200, 13.4050, "20");
		save("eifel-climb", ActivityType.CLIMBING, null, 3, 50.6897, 6.4836, null);
		this.events.flush();
	}

	@Test
	void filtersByType() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("type", "HIKING"))
				.andExpect(jsonPath("$.items[*].slug", contains("aachen-hike", "berlin-hike")));
	}

	@Test
	void typesCombineAsEither() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("type", "CYCLING").param("type", "CLIMBING"))
				.andExpect(jsonPath("$.items[*].slug", contains("cologne-ride", "eifel-climb")));
	}

	@Test
	void filtersByDifficulty() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("difficulty", "EASY").param("difficulty", "HARD"))
				.andExpect(jsonPath("$.items[*].slug", contains("aachen-hike", "berlin-hike")));
	}

	/** Days are Berlin days: "to" includes the whole of its day. */
	@Test
	void filtersByDays() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de")
						.param("from", DAY.plusDays(1).toString()).param("to", DAY.plusDays(2).toString()))
				.andExpect(jsonPath("$.items[*].slug", contains("cologne-ride", "berlin-hike")));
	}

	@Test
	void filtersByDistance() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("minKm", "15").param("maxKm", "50"))
				.andExpect(jsonPath("$.items[*].slug", contains("cologne-ride", "berlin-hike")));
	}

	/**
	 * Cologne is about 65 km from Aachen and Berlin about 520. If the radius were
	 * read in degrees, 80000 of them would take in the whole planet and Berlin
	 * would be in the answer.
	 */
	@Test
	void nearMeIsMetresOnTheSphere() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de")
						.param("lat", "50.7753").param("lon", "6.0839").param("radiusKm", "80"))
				.andExpect(jsonPath("$.items[*].slug", contains("aachen-hike", "cologne-ride", "eifel-climb")));
		this.mvc.perform(get("/v1/events").param("locale", "de")
						.param("lat", "50.7753").param("lon", "6.0839").param("radiusKm", "30"))
				.andExpect(jsonPath("$.items[*].slug", contains("aachen-hike", "eifel-climb")));
	}

	/** An event without a stated price is neither free nor paid. */
	@Test
	void filtersByPrice() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "en").param("price", "free"))
				.andExpect(jsonPath("$.items[*].slug", contains("aachen-hike")))
				.andExpect(jsonPath("$.items[0].priceAmount").value(0));
		this.mvc.perform(get("/v1/events").param("locale", "en").param("price", "paid"))
				.andExpect(jsonPath("$.items[*].slug", contains("cologne-ride")))
				.andExpect(jsonPath("$.items[0].priceCurrency").value("EUR"));
		this.mvc.perform(get("/v1/events").param("locale", "en").param("price", "cheap"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void filtersCombine() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("type", "HIKING")
						.param("lat", "50.7753").param("lon", "6.0839").param("radiusKm", "80"))
				.andExpect(jsonPath("$.items[*].slug", contains("aachen-hike")));
	}

	@Test
	void theCardCarriesWhatTheListShows() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "ru").param("type", "CYCLING"))
				.andExpect(jsonPath("$.items[0].name").value("RU cologne-ride"))
				.andExpect(jsonPath("$.items[0].organizer").value("Testgruppe"))
				.andExpect(jsonPath("$.items[0].distanceKm").value(45.0));
	}

	@Test
	void unknownFilterValuesAreRefused() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("type", "PARAGLIDING"))
				.andExpect(status().isBadRequest());
		this.mvc.perform(get("/v1/events").param("locale", "de").param("from", "tomorrow"))
				.andExpect(status().isBadRequest());
		this.mvc.perform(get("/v1/events").param("locale", "de").param("lat", "50.7").param("lon", "6.0"))
				.andExpect(status().isBadRequest());
		this.mvc.perform(get("/v1/events").param("locale", "de").param("minKm", "30").param("maxKm", "10"))
				.andExpect(status().isBadRequest());
	}

	/** The map asks for a rectangle and gets only what lies in it. */
	@Test
	void theMapAnswersForTheViewportOnly() throws Exception {
		this.mvc.perform(get("/v1/events/points").param("locale", "en")
						.param("south", "50.5").param("west", "5.8").param("north", "51.1").param("east", "7.2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].slug", containsInAnyOrder("aachen-hike", "cologne-ride", "eifel-climb")))
				.andExpect(jsonPath("$.items[0].latitude").isNumber())
				.andExpect(jsonPath("$.items[0].placeName").isString())
				.andExpect(jsonPath("$.items[0].description").doesNotExist())
				.andExpect(jsonPath("$.truncated").value(false));
	}

	@Test
	void theMapHonoursTheFilters() throws Exception {
		this.mvc.perform(get("/v1/events/points").param("locale", "en").param("type", "HIKING")
						.param("south", "50.5").param("west", "5.8").param("north", "51.1").param("east", "7.2"))
				.andExpect(jsonPath("$.items[*].slug", contains("aachen-hike")));
	}

	@Test
	void anUpsideDownViewportIsRefused() throws Exception {
		this.mvc.perform(get("/v1/events/points").param("locale", "en")
						.param("south", "51").param("west", "7").param("north", "50").param("east", "6"))
				.andExpect(status().isBadRequest());
	}

}
