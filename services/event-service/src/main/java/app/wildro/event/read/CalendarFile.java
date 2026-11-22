package app.wildro.event.read;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import app.wildro.event.domain.EventStatus;

/**
 * One event as an iCalendar file (RFC 5545), for "add to calendar": Apple's
 * Calendar, Outlook and anything else that opens a .ics.
 *
 * <ul>
 * <li>Times are written in UTC with a Z, so no time zone block is needed and
 * every calendar shows the Berlin time the event has, converted to its own.</li>
 * <li>An event with an end becomes a block from start to end. One without an
 * end gets no DTEND: the calendar shows its start, and nothing invents a
 * length the organiser never gave.</li>
 * <li>The UID is stable per event, so a second import updates the entry
 * instead of doubling it, and a cancelled event arrives as STATUS:CANCELLED.</li>
 * <li>Text is escaped as the RFC asks and lines are folded at 75 octets on
 * character boundaries, so umlauts and Cyrillic survive.</li>
 * </ul>
 */
public final class CalendarFile {

	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
		.withZone(ZoneOffset.UTC);

	/** How much of the description goes along: enough to recognise the event, the page has the rest. */
	private static final int DESCRIPTION_CHARS = 600;

	private CalendarFile() {
	}

	public static String of(EventPage event, String pageUrl, Instant now) {
		StringBuilder out = new StringBuilder();
		line(out, "BEGIN:VCALENDAR");
		line(out, "VERSION:2.0");
		line(out, "PRODID:-//wildro//events//EN");
		line(out, "CALSCALE:GREGORIAN");
		line(out, "METHOD:PUBLISH");
		line(out, "BEGIN:VEVENT");
		line(out, "UID:" + event.slug() + "@wildro.app");
		line(out, "DTSTAMP:" + UTC.format(now));
		line(out, "DTSTART:" + UTC.format(event.startsAt()));
		if (event.endsAt() != null) {
			line(out, "DTEND:" + UTC.format(event.endsAt()));
		}
		line(out, "SUMMARY:" + text(event.name()));
		line(out, "LOCATION:" + text(event.placeName()));
		line(out, String.format(Locale.ROOT, "GEO:%.6f;%.6f", event.latitude(), event.longitude()));
		line(out, "DESCRIPTION:" + text(shortened(event.description()) + "\n\n" + pageUrl));
		line(out, "URL:" + pageUrl);
		line(out, "STATUS:" + (event.status() == EventStatus.CANCELLED ? "CANCELLED" : "CONFIRMED"));
		line(out, "END:VEVENT");
		line(out, "END:VCALENDAR");
		return out.toString();
	}

	/** TEXT values: backslash, semicolon and comma escaped, line breaks as \n (RFC 5545, 3.3.11). */
	static String text(String value) {
		return value == null ? ""
				: value.replace("\\", "\\\\")
					.replace(";", "\\;")
					.replace(",", "\\,")
					.replace("\r\n", "\n")
					.replace("\r", "\n")
					.replace("\n", "\\n");
	}

	private static String shortened(String description) {
		if (description == null) {
			return "";
		}
		String trimmed = description.strip();
		if (trimmed.length() <= DESCRIPTION_CHARS) {
			return trimmed;
		}
		int cut = trimmed.lastIndexOf(' ', DESCRIPTION_CHARS);
		return trimmed.substring(0, cut > DESCRIPTION_CHARS / 2 ? cut : DESCRIPTION_CHARS) + " …";
	}

	/**
	 * Writes one content line, folded so no physical line passes 75 octets: a
	 * continuation starts with a space, and a fold never splits a character.
	 */
	static void line(StringBuilder out, String content) {
		int octets = 0;
		int limit = 75;
		for (int i = 0; i < content.length();) {
			int codePoint = content.codePointAt(i);
			int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
			if (octets + size > limit) {
				out.append("\r\n ");
				octets = 1;
				limit = 75;
			}
			out.appendCodePoint(codePoint);
			octets += size;
			i += Character.charCount(codePoint);
		}
		out.append("\r\n");
	}

}
