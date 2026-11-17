package app.wildro.event.read;

import app.wildro.event.TestcontainersConfiguration;
import app.wildro.event.domain.ActivityType;
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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Text search over name, organiser and place in every language, against a real
 * Postgres with pg_trgm: the LIKE and the typo match both run in the database,
 * so only a real one shows they work.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventSearchTest {

	private static final LocalDate DAY = LocalDate.now(Event.ZONE).plusDays(30);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private EventRepository events;

	private void save(String slug, int dayOffset, String organizer, String nameDe, String placeDe, String nameRu,
			String placeRu) {
		this.events.save(Event.builder()
				.slug(slug)
				.status(EventStatus.PUBLISHED)
				.activityType(ActivityType.HIKING)
				.startsAt(DAY.plusDays(dayOffset).atTime(LocalTime.of(10, 0)).atZone(Event.ZONE).toInstant())
				.startPoint(GeoPoints.of(50.7, 6.5))
				.organizer(organizer)
				.source("https://example.org/" + slug, null)
				.translation("de", new EventTranslation(nameDe, placeDe, "Wald und Wiesen."))
				.translation("en", new EventTranslation(nameDe, placeDe, "Woods and meadows."))
				.translation("ru", new EventTranslation(nameRu, placeRu, "Лес и луга."))
				.build());
	}

	@BeforeEach
	void seed() {
		save("huerth", 0, "Wandergesellen Alt-Hürth 1991 e.V.", "Wandertag am 4. Advent", "Löhrerhof, Alt-Hürth",
				"День ходьбы на четвёртый адвент", "Лёрерхоф, Альт-Хюрт");
		save("monschau", 1, "Nationalpark Eifel", "Rangertour Monschau", "Wanderparkplatz, Monschau",
				"Тур с рейнджером в Моншау", "Парковка, Моншау");
		save("monreal", 2, "Sportfreunde Monreal-Reudelsterz e.V.", "Jubiläums-Wandertag", "Sportplatz Monreal",
				"Юбилейный день ходьбы", "Спортплощадка Монреаля");
		this.events.flush();
	}

	@Test
	void findsByPlaceWithoutTheUmlaut() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("q", "Hurth"))
				.andExpect(jsonPath("$.items[*].slug", contains("huerth")));
	}

	@Test
	void findsByOrganiser() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "en").param("q", "sportfreunde"))
				.andExpect(jsonPath("$.items[*].slug", contains("monreal")));
	}

	/** The Russian name is found from any locale's page: every language is searched. */
	@Test
	void findsByTheNameInAnotherLanguage() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("q", "Монреаль"))
				.andExpect(jsonPath("$.items[*].slug", contains("monreal")));
	}

	@Test
	void forgivesATypo() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("q", "Monschou"))
				.andExpect(jsonPath("$.items[*].slug", contains("monschau")));
	}

	@Test
	void everyWordMustBeFound() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("q", "wandertag advent"))
				.andExpect(jsonPath("$.items[*].slug", contains("huerth")));
	}

	/** The description is not searched: "Wald" is in every one of them. */
	@Test
	void theDescriptionIsNotSearched() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("q", "Wiesen"))
				.andExpect(jsonPath("$.items", empty()));
	}

	@Test
	void anEmptyOrOneLetterQueryFiltersNothing() throws Exception {
		this.mvc.perform(get("/v1/events").param("locale", "de").param("q", " a "))
				.andExpect(jsonPath("$.items[*].slug", contains("huerth", "monschau", "monreal")));
	}

	@Test
	void theMapSearchesToo() throws Exception {
		this.mvc.perform(get("/v1/events/points").param("locale", "de").param("q", "monschau")
						.param("south", "50").param("west", "6").param("north", "51").param("east", "7"))
				.andExpect(jsonPath("$.items[*].slug", contains("monschau")));
	}

}
