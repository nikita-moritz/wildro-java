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

export type Channel = "whatsapp" | "telegram" | "facebook" | "instagram";

export interface ShareFacts {
  name: string;
  /** Date and time as the page writes them. */
  when: string;
  placeName: string;
  /** Activity, distance and difficulty in words, already joined. */
  facts: string;
  description: string;
  url: string;
}

/** The words before each fact of a shared text: "Datum:", "Start:". */
export interface ShareLabels {
  date: string;
  start: string;
  type: string;
  area: string;
}

/**
 * The text to post in a chat or a group, in the page's language. WhatsApp
 * shows *this* in bold; Telegram, Facebook and an Instagram caption take plain text.
 * Each fact behind a word, no symbols (owner's call, 2026-11-24): a message, not a flyer.
 */
export function shareText(channel: Channel, facts: ShareFacts, labels: ShareLabels, moreLabel: string): string {
  const title = channel === "whatsapp" ? `*${facts.name}*` : facts.name;
  return [
    title,
    `${labels.date}: ${facts.when}`,
    `${labels.start}: ${facts.placeName}`,
    facts.facts ? `${labels.type}: ${facts.facts}` : "",
    "",
    shortened(facts.description, 240),
    "",
    `${moreLabel}: ${facts.url}`,
  ]
    .filter((line, index, lines) => line !== "" || (lines[index - 1] ?? "") !== "")
    .join("\n");
}

export interface SharedCommunity {
  name: string;
  area: string;
  /** "12 Mitglieder", already in words. */
  members: string;
  description: string;
  /** "Die nächsten Events", the heading of `upcoming`. */
  upcomingLabel: string;
  /** The next few outings, each "Sa., 5. Dez.: Morgenlauf"; empty when nothing is planned. */
  upcoming: string[];
  url: string;
}

/**
 * A group's digest to post in a chat: who it is, its next outings, the way in.
 * The same shape as an event's text, so the two read alike in one chat.
 */
export function communityShareText(channel: Channel, group: SharedCommunity, labels: ShareLabels, moreLabel: string): string {
  return [
    channel === "whatsapp" ? `*${group.name}*` : group.name,
    `${labels.area}: ${group.area} · ${group.members}`,
    "",
    ...(group.upcoming.length > 0 ? [`${group.upcomingLabel}:`, ...group.upcoming, ""] : []),
    shortened(group.description, group.upcoming.length > 0 ? 160 : 240),
    "",
    `${moreLabel}: ${group.url}`,
  ]
    .filter((line, index, lines) => line !== "" || (index > 0 && (lines[index - 1] ?? "") !== ""))
    .join("\n");
}

/**
 * A calendar to subscribe to, from its https address. webcal:// makes the
 * phone, Apple's Calendar and Outlook offer to subscribe instead of importing
 * once; Google takes the same address as its cid.
 */
export function subscriptionUrls(feedUrl: string): { webcal: string; google: string } {
  const webcal = feedUrl.replace(/^https?:\/\//, "webcal://");
  return { webcal, google: `https://calendar.google.com/calendar/render?${new URLSearchParams({ cid: webcal })}` };
}

/** WhatsApp with the text filled in; the person picks the chat or group. */
export function whatsappUrl(text: string): string {
  return `https://wa.me/?text=${encodeURIComponent(text)}`;
}

/** Telegram's share form: the link and the text, the person picks the chat. */
export function telegramUrl(url: string, text: string): string {
  // Telegram shows the link above the text itself, so a last line carrying it is dropped.
  const lines = text.trimEnd().split("\n");
  const withoutLink = lines.at(-1)?.includes(url) ? lines.slice(0, -1).join("\n").trimEnd() : text.trimEnd();
  return `https://t.me/share/url?${new URLSearchParams({ url, text: withoutLink })}`;
}

/** Facebook takes only the link; the text goes in by pasting. */
export function facebookUrl(url: string): string {
  return `https://www.facebook.com/sharer/sharer.php?${new URLSearchParams({ u: url })}`;
}
