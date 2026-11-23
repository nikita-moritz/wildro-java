package app.wildro.event.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.EventKind;
import app.wildro.event.domain.EventStatus;

/** What a calendar app reads out of the file: RFC 5545, rule by rule. */
class CalendarFileTest {

	private static final Instant NOW = Instant.parse("2026-11-22T17:00:00Z");

	private static final String URL = "https://java.wildro.app/de/event/eifelsteig-2026-12-05";

	@Test
	void winterTimeIsWrittenInUtcAndAnEndMakesABlock() {
		// 09:30 in Berlin in December is 08:30 UTC.
		String ics = CalendarFile.of(event(EventStatus.PUBLISHED, "2026-12-05T08:30:00Z", "2026-12-05T13:00:00Z"), URL, NOW);
		assertThat(ics).contains("DTSTART:20261205T083000Z\r\n", "DTEND:20261205T130000Z\r\n",
				"UID:eifelsteig-2026-12-05@wildro.app\r\n", "STATUS:CONFIRMED\r\n", "URL:" + URL + "\r\n",
				"GEO:50.775300;6.083900\r\n");
		assertThat(ics).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n").endsWith("END:VCALENDAR\r\n");
	}

	@Test
	void summerTimeToo() {
		String ics = CalendarFile.of(event(EventStatus.PUBLISHED, "2026-07-04T07:00:00Z", null), URL, NOW);
		assertThat(ics).contains("DTSTART:20260704T070000Z\r\n");
	}

	@Test
	void withoutAnEndNoLengthIsInvented() {
		assertThat(CalendarFile.of(event(EventStatus.PUBLISHED, "2026-12-05T08:30:00Z", null), URL, NOW))
			.doesNotContain("DTEND").doesNotContain("DURATION");
	}

	@Test
	void aCancelledEventSaysSo() {
		assertThat(CalendarFile.of(event(EventStatus.CANCELLED, "2026-12-05T08:30:00Z", null), URL, NOW))
			.contains("STATUS:CANCELLED\r\n");
	}

	@Test
	void aFeedIsOneNamedCalendarWithOneEntryPerEvent() {
		String ics = CalendarFile.feed("Wandertreff Aachen, Eifel", List.of(
				event(EventStatus.PUBLISHED, "2026-12-05T08:30:00Z", null),
				event(EventStatus.CANCELLED, "2026-12-12T08:30:00Z", null)), (each) -> URL, NOW);
		assertThat(ics).contains("X-WR-CALNAME:Wandertreff Aachen\\, Eifel\r\n", "X-PUBLISHED-TTL:PT6H\r\n",
				"LAST-MODIFIED:20261122T170000Z\r\n");
		assertThat(ics.split("BEGIN:VEVENT", -1)).hasSize(3);
		assertThat(ics.split("BEGIN:VCALENDAR", -1)).hasSize(2);
	}

	@Test
	void aSingleEventFileHasNoCalendarName() {
		assertThat(CalendarFile.of(event(EventStatus.PUBLISHED, "2026-12-05T08:30:00Z", null), URL, NOW))
			.doesNotContain("X-WR-CALNAME").doesNotContain("REFRESH-INTERVAL");
	}

	@Test
	void textIsEscapedAsTheRfcAsks() {
		assertThat(CalendarFile.text("Treff: Parkplatz, Ecke A; dann\nlos \\ weiter"))
			.isEqualTo("Treff: Parkplatz\\, Ecke A\\; dann\\nlos \\\\ weiter");
	}

	@Test
	void longLinesFoldAt75OctetsWithoutSplittingACharacter() {
		StringBuilder out = new StringBuilder();
		CalendarFile.line(out, "SUMMARY:" + "Поход по Айфелю ".repeat(12));
		List<String> lines = Arrays.asList(out.toString().split("\r\n"));
		assertThat(lines).hasSizeGreaterThan(2);
		for (String line : lines) {
			assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
			assertThat(line).doesNotContain("�");
		}
		assertThat(lines.subList(1, lines.size())).allMatch((line) -> line.startsWith(" "));
		// Unfolding gives back what went in.
		assertThat(out.toString().replace("\r\n ", "").strip()).isEqualTo("SUMMARY:" + "Поход по Айфелю ".repeat(12).strip());
	}

	private static EventPage event(EventStatus status, String start, String end) {
		return new EventPage(1, "eifelsteig-2026-12-05", status, "Eifelsteig, Etappe 3", "Parkplatz Kalterherberg",
				"Wir wandern gemeinsam.", ActivityType.HIKING, null, Instant.parse(start),
				end == null ? null : Instant.parse(end), Instant.parse(start), 50.7753, 6.0839, "Wandertreff Aachen",
				null, null, BigDecimal.TEN, null, null, null, null, List.of(), List.of(), EventKind.OWN, "de", null,
				NOW);
	}

}
