package app.wildro.event.domain;

import app.wildro.event.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Against a real PostGIS, because the things worth checking here are the ones a
 * mock cannot have an opinion about: whether a JTS point round-trips through a
 * {@code geography} column, whether the spatial index answers a proximity query,
 * and whether the schema refuses what the domain refuses.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EventRepositoryTest {

	private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

	/** Longitude first: JTS takes x, y, and x is the meridian. Getting this backwards puts Aachen in Somalia. */
	private static final Point AACHEN = GEOMETRY.createPoint(new Coordinate(6.0839, 50.7753));

	private static final Point COLOGNE = GEOMETRY.createPoint(new Coordinate(6.9603, 50.9375));

	private static final Point BERLIN_CITY = GEOMETRY.createPoint(new Coordinate(13.4050, 52.5200));

	@Autowired
	private EventRepository events;

	@Autowired
	private JdbcTemplate jdbc;

	private static Instant at(String isoLocal) {
		return LocalDateTime.parse(isoLocal).atZone(Event.ZONE).toInstant();
	}

	private static Event.Builder event(String slug, Point where) {
		return Event.builder()
				.slug(slug)
				.kind(EventKind.EXTERNAL)
				.status(EventStatus.PUBLISHED)
				.activityType(ActivityType.HIKING)
				.startsAt(at("2026-10-03T09:00:00"))
				.startPoint(where)
				.organizer("Eifelverein Aachen")
				.source("https://example.org/gruppe/" + slug, at("2026-09-20T12:00:00"))
				.translation("de", new EventTranslation("Wanderung", "Aachen, Deutschland", "Auftakt."))
				.translation("en", new EventTranslation("Hike", "Aachen, Germany", "The opening leg."))
				.translation("ru", new EventTranslation("Поход", "Ахен, Германия", "Первый отрезок."));
	}

	@Test
	void savesAndReadsBackEveryField() {
		events.saveAndFlush(event("eifelsteig-1", AACHEN).difficulty(Difficulty.MEDIUM).build());

		Event found = events.findBySlug("eifelsteig-1").orElseThrow();

		assertThat(found.getId()).isNotNull();
		assertThat(found.getActivityType()).isEqualTo(ActivityType.HIKING);
		assertThat(found.getDifficulty()).isEqualTo(Difficulty.MEDIUM);
		assertThat(found.getStartPoint().getX()).isEqualTo(AACHEN.getX(), org.assertj.core.data.Offset.offset(1e-9));
		assertThat(found.getStartPoint().getY()).isEqualTo(AACHEN.getY(), org.assertj.core.data.Offset.offset(1e-9));
		assertThat(found.getTranslations()).containsOnlyKeys("de", "en", "ru");
		assertThat(found.translation("de").getName()).isEqualTo("Wanderung");
	}

	@Test
	void theSlugIsUnique() {
		events.saveAndFlush(event("same-slug", AACHEN).build());

		assertThatThrownBy(() -> events.saveAndFlush(event("same-slug", COLOGNE).build()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	/**
	 * Proximity has to be a database query rather than application arithmetic, so
	 * this is the shape the feed's filter will take. Distances come back in metres
	 * because the column is {@code geography}.
	 */
	@Test
	void findsWhatIsNearAndLeavesOutWhatIsFar() {
		events.saveAndFlush(event("near-aachen", AACHEN).build());
		events.saveAndFlush(event("near-cologne", COLOGNE).build());
		events.saveAndFlush(event("far-berlin", BERLIN_CITY).build());

		List<String> within80km = jdbc.queryForList("""
				SELECT slug FROM events
				WHERE ST_DWithin(start_point, ST_MakePoint(?, ?)::geography, 80000)
				ORDER BY start_point <-> ST_MakePoint(?, ?)::geography
				""", String.class, AACHEN.getX(), AACHEN.getY(), AACHEN.getX(), AACHEN.getY());

		assertThat(within80km).containsExactly("near-aachen", "near-cologne");
	}

	@Test
	void theSpatialIndexIsTheOneAnswering() {
		events.saveAndFlush(event("indexed", AACHEN).build());

		// A handful of rows is below the threshold where the planner bothers with an
		// index, so this asserts the index exists rather than that it was chosen.
		List<String> indexes = jdbc.queryForList(
				"SELECT indexname FROM pg_indexes WHERE tablename = 'events'", String.class);

		assertThat(indexes).contains("events_start_point_idx");
	}

	/**
	 * SPEC.md §0 puts this in the schema and not only in a form check, so the
	 * schema is what gets tested. Bypassing the domain is the point: the guarantee
	 * must hold for the ingestion CLI and for a human with psql too.
	 */
	@Test
	void theDatabaseRefusesAnEventWithoutCoordinates() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO events (slug, kind, status, activity_type, starts_at, organizer, source_url)
				VALUES ('no-point', 'EXTERNAL', 'PUBLISHED', 'HIKING', now(), 'someone', 'https://example.org')
				"""))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theDatabaseRefusesAnExternalEventWithoutASource() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO events (slug, kind, status, activity_type, starts_at, start_point, organizer)
				VALUES ('no-source', 'EXTERNAL', 'PUBLISHED', 'HIKING', now(),
				        ST_MakePoint(6.08, 50.77)::geography, 'someone')
				"""))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theDatabaseRefusesAnUnknownActivityType() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO events (slug, kind, status, activity_type, starts_at, start_point, organizer, source_url)
				VALUES ('unknown-type', 'EXTERNAL', 'PUBLISHED', 'PARAGLIDING', now(),
				        ST_MakePoint(6.08, 50.77)::geography, 'someone', 'https://example.org')
				"""))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingAnEventTakesItsTranslationsWithIt() {
		Event saved = events.saveAndFlush(event("to-delete", AACHEN).build());

		events.delete(saved);
		events.flush();

		Integer left = jdbc.queryForObject(
				"SELECT count(*) FROM event_translations WHERE event_id = ?", Integer.class, saved.getId());
		assertThat(left).isZero();
	}

}
