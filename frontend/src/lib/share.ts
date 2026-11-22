/**
 * Sharing an event and putting it in a calendar (owner's plan, 2026-11-22).
 * Nothing here talks to another site: every address is built from the event
 * and only opened when the visitor presses something.
 */

export interface SharedEvent {
  name: string;
  placeName: string;
  description: string;
  startsAt: string;
  endsAt: string | null;
  /** The event's page, absolute. */
  url: string;
}

/** "2026-12-05T08:30:00Z" as Google wants it: 20261205T083000Z. */
function compactUtc(iso: string): string {
  return new Date(iso).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}

function shortened(text: string, max: number): string {
  const trimmed = text.trim();
  if (trimmed.length <= max) {
    return trimmed;
  }
  const cut = trimmed.lastIndexOf(" ", max);
  return `${trimmed.slice(0, cut > max / 2 ? cut : max)} …`;
}

/**
 * Google Calendar's "new event" form, filled in. The same rule as the .ics the
 * event service writes: with an end a block, without one no invented length
 * (start and end the same moment).
 */
export function googleCalendarUrl(event: SharedEvent): string {
  const start = compactUtc(event.startsAt);
  const end = event.endsAt ? compactUtc(event.endsAt) : start;
  const query = new URLSearchParams({
    action: "TEMPLATE",
    text: event.name,
    dates: `${start}/${end}`,
    location: event.placeName,
    details: `${shortened(event.description, 600)}\n\n${event.url}`,
  });
  return `https://calendar.google.com/calendar/render?${query}`;
}
