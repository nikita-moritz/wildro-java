package app.wildro.event.admin;

import app.wildro.event.domain.ActivityType;
import app.wildro.event.domain.EventStatus;

import java.time.Instant;

/**
 * One line of an admin listing. Read straight from a query with a locale
 * predicate on the translations join, so each event yields exactly one row and
 * a page boundary means what it says: joining every translation would triple the
 * rows and corrupt the paging (AGENTS.md, the five traps).
 */
public record EventSummary(
		String slug,
		EventStatus status,
		ActivityType activityType,
		Instant startsAt,
		Instant passesAt,
		String name) {
}
