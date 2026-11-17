package app.wildro.event.domain;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules an event carries on its own, with no database in sight. They are
 * checked here rather than only in the schema because a constraint violation
 * surfaces as a driver exception several layers away from whoever broke it.
 */
class EventTest {

	private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

	/** Aachen, roughly the Elisenbrunnen. */
	private static final Point AACHEN = GEOMETRY.createPoint(new Coordinate(6.0839, 50.7753));

	private static Instant berlin(String isoLocal) {
		return LocalDateTime.parse(isoLocal).atZone(Event.ZONE).toInstant();
	}

	private static Event.Builder valid() {
		return Event.builder()
				.slug("eifelsteig-etappe-1")
				.kind(EventKind.EXTERNAL)
				.status(EventStatus.PUBLISHED)
				.activityType(ActivityType.HIKING)
				.startsAt(berlin("2026-10-03T09:00:00"))
				.startPoint(AACHEN)
				.organizer("Eifelverein Aachen")
				.source("https://example.org/gruppe/wanderung", berlin("2026-09-20T12:00:00"))
				.translation("de", new EventTranslation("Eifelsteig Etappe 1", "Aachen, Deutschland", "Auftakt."))
				.translation("en", new EventTranslation("Eifelsteig stage 1", "Aachen, Germany", "The opening leg."))
				.translation("ru", new EventTranslation("Айфельштайг, этап 1", "Ахен, Германия", "Первый отрезок."));
	}

	@Test
	void buildsWithEveryRequiredField() {
		Event event = valid().build();

		assertThat(event.getSlug()).isEqualTo("eifelsteig-etappe-1");
		assertThat(event.getStartPoint()).isEqualTo(AACHEN);
		assertThat(event.translation("ru").getPlaceName()).isEqualTo("Ахен, Германия");
	}

	@Test
	void anExternalEventWithoutASourceIsRefused() {
		assertThatThrownBy(() -> valid().source(null, null).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("link to its source");
	}

	@Test
	void anEventWithoutAStartPointIsRefused() {
		assertThatThrownBy(() -> valid().startPoint(null).build())
				.isInstanceOfSatisfying(InvalidEventException.class,
						ex -> assertThat(ex.getField()).isEqualTo("startPoint"));
	}

	@Test
	void anEventMissingALanguageIsRefused() {
		assertThatThrownBy(() -> Event.builder()
				.slug("x")
				.activityType(ActivityType.HIKING)
				.startsAt(berlin("2026-10-03T09:00:00"))
				.startPoint(AACHEN)
				.organizer("someone")
				.source("https://example.org", null)
				.translation("de", new EventTranslation("Name", "Aachen", "Text"))
				.build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("written in all of");
	}

	@Test
	void endMustBeAfterStart() {
		assertThatThrownBy(() -> valid().endsAt(berlin("2026-10-03T08:00:00")).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("after startsAt");
	}

	@Test
	void blankTranslationTextIsRefused() {
		assertThatThrownBy(() -> new EventTranslation("Name", "Aachen", "   "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("description");
	}

	@Test
	void anEventWithAnEndIsPastOnlyAfterThatEnd() {
		Event event = valid().endsAt(berlin("2026-10-03T17:00:00")).build();

		assertThat(event.hasPassed(berlin("2026-10-03T16:59:00"))).isFalse();
		assertThat(event.hasPassed(berlin("2026-10-03T17:01:00"))).isTrue();
	}

	/**
	 * The rule that decides what the feed shows. An event with no end is still
	 * running for the rest of its starting day, in Europe/Berlin and not in
	 * whatever zone the server happens to sit in.
	 */
	@Test
	void anEventWithoutAnEndRunsUntilTheEndOfItsStartingDay() {
		Event event = valid().build();

		assertThat(event.hasPassed(berlin("2026-10-03T23:59:00"))).isFalse();
		assertThat(event.hasPassed(berlin("2026-10-04T00:01:00"))).isTrue();
	}

	@Test
	void theZoneIsBerlinEvenWhenTheMachineIsElsewhere() {
		Event event = valid().build();

		// Same instant, named from Tokyo. The answer must not move with the caller.
		Instant justBeforeMidnightInBerlin = LocalDateTime.parse("2026-10-04T06:59:00")
				.atZone(ZoneId.of("Asia/Tokyo"))
				.toInstant();

		assertThat(event.hasPassed(justBeforeMidnightInBerlin)).isFalse();
	}

	@Test
	void aCancelledEventIsNotUpcoming() {
		Event event = valid().build();
		Instant beforeItStarts = berlin("2026-10-01T09:00:00");
		assertThat(event.isUpcoming(beforeItStarts)).isTrue();

		event.cancel();

		assertThat(event.isUpcoming(beforeItStarts)).isFalse();
	}

	@Test
	void aDraftIsNotUpcomingEither() {
		Event event = valid().status(EventStatus.DRAFT).build();

		assertThat(event.isUpcoming(berlin("2026-10-01T09:00:00"))).isFalse();
	}

	/**
	 * The one that matters most on stage one: every event is external, and no
	 * capacity from someone else's chat may reach the page.
	 */
	@Test
	void capacityIsNeverVisibleOnAnExternalEvent() {
		Event event = valid().maxParticipants(12).build();

		assertThat(event.getVisibleCapacity()).isNull();
	}

	@Test
	void capacityIsVisibleOnAnOwnEvent() {
		Event event = valid().kind(EventKind.OWN).source(null, null).maxParticipants(12).build();

		assertThat(event.getVisibleCapacity()).isEqualTo(12);
	}

	@Test
	void onlyAnExternalEventHasASourceToCheck() {
		Event own = valid().kind(EventKind.OWN).source(null, null).build();

		assertThatThrownBy(() -> own.markSourceChecked(Instant.now()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anEventHasNoLinksUnlessGiven() {
		Event event = valid().build();

		assertThat(event.links(LinkScope.EVENT)).isEmpty();
		assertThat(event.links(LinkScope.COMMUNITY)).isEmpty();
	}

	/** Event links come first whatever order the builder was fed, each scope in the order written. */
	@Test
	void linksAreKeptPerScopeInTheOrderWritten() {
		Event event = valid()
				.link(new EventLink(LinkScope.COMMUNITY, "https://t.me/gruppe"))
				.link(new EventLink(LinkScope.EVENT, "https://www.facebook.com/events/1"))
				.link(new EventLink(LinkScope.EVENT, "https://t.me/gruppe/42"))
				.build();

		assertThat(event.links(LinkScope.EVENT)).extracting(EventLink::getUrl)
				.containsExactly("https://www.facebook.com/events/1", "https://t.me/gruppe/42");
		assertThat(event.links(LinkScope.COMMUNITY)).extracting(EventLink::getUrl)
				.containsExactly("https://t.me/gruppe");
	}

	@Test
	void theSameLinkTwiceInOneScopeIsRefused() {
		assertThatThrownBy(() -> valid()
				.link(new EventLink(LinkScope.COMMUNITY, "https://t.me/gruppe"))
				.link(new EventLink(LinkScope.COMMUNITY, "https://t.me/gruppe"))
				.build())
				.isInstanceOf(InvalidEventException.class)
				.extracting("field").isEqualTo("links/community");
	}

	/** The same address may be both: a small group often has one chat for everything. */
	@Test
	void theSameAddressMayServeBothScopes() {
		Event event = valid()
				.link(new EventLink(LinkScope.EVENT, "https://t.me/gruppe"))
				.link(new EventLink(LinkScope.COMMUNITY, "https://t.me/gruppe"))
				.build();

		assertThat(event.links(LinkScope.EVENT)).hasSize(1);
		assertThat(event.links(LinkScope.COMMUNITY)).hasSize(1);
	}

	@Test
	void moreLinksThanAHandfulAreRefused() {
		Event.Builder builder = valid();
		for (int i = 0; i <= Event.MAX_LINKS_PER_SCOPE; i++) {
			builder.link(new EventLink(LinkScope.EVENT, "https://t.me/gruppe/" + i));
		}

		assertThatThrownBy(builder::build)
				.isInstanceOf(InvalidEventException.class)
				.extracting("field").isEqualTo("links/event");
	}

	@Test
	void aCoverWithoutItsCreditIsRefused() {
		assertThatThrownBy(() -> valid().coverImageUrl("https://media.wildro.app/x.webp").build())
				.isInstanceOf(InvalidEventException.class)
				.extracting("field").isEqualTo("coverCredit");
	}

	@Test
	void aCreditWithoutACoverIsRefused() {
		assertThatThrownBy(() -> valid().coverCredit(credit()).build())
				.isInstanceOf(InvalidEventException.class)
				.extracting("field").isEqualTo("coverCredit");
	}

	@Test
	void aCoverWithItsCreditIsKept() {
		Event event = valid().coverImageUrl("https://media.wildro.app/x.webp").coverCredit(credit()).build();

		assertThat(event.getCoverCredit()).isEqualTo(credit());
	}

	@Test
	void aCreditLinkThatIsNotHttpsIsRefused() {
		assertThatThrownBy(() -> new CoverCredit("A. Author", "CC BY-SA 4.0", "http://creativecommons.org/x", "https://commons.wikimedia.org/x"))
				.isInstanceOf(InvalidEventException.class)
				.extracting("field").isEqualTo("licenseUrl");
	}

	private static CoverCredit credit() {
		return new CoverCredit("A. Author", "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/",
				"https://commons.wikimedia.org/wiki/File:Example.jpg");
	}

}
