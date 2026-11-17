import "server-only";

import { cacheLife, cacheTag } from "next/cache";
import { z } from "zod";

import { eventServiceUrl } from "@/lib/env";
import { EMPTY_FILTER, filterToParams, type FeedFilter } from "@/lib/feed-filter";
import type { Locale } from "@/lib/locales";
import { ACTIVITY_TYPES, DIFFICULTIES } from "@/lib/vocabulary";

export type { ActivityType, Difficulty } from "@/lib/vocabulary";

/**
 * The event service's public read API, as this frontend sees it.
 *
 * Every read is cached and tagged. The service calls /api/revalidate with the
 * tags a write affects, so a page changes when its data does rather than on a
 * timer; `hours` is only the floor for what nobody writes but the clock moves,
 * such as an event passing from upcoming to past. No public page therefore
 * calls the service in its hot path (AGENTS.md, product goal).
 *
 * Responses are parsed with zod: the service is another program, and a field
 * it renamed should fail here with a message, not render as "undefined".
 */

/**
 * The invalidation contract with the event service, written once. A write to an
 * event invalidates its own page and the feed; the map joins when it exists.
 */
export const TAGS = {
  feed: "events:feed",
  event: (slug: string) => `event:${slug}`,
} as const;

/** Twelve divides into rows of one, two, three and four cards. */
export const FEED_PAGE_SIZE = 12;

/** Matches the service's bound on feed depth (EventReadController.MAX_PAGE, zero-based). */
export const MAX_FEED_PAGE = 100;

const instant = z.iso.datetime({ offset: true });

const eventCardSchema = z.object({
  slug: z.string(),
  name: z.string(),
  placeName: z.string(),
  activityType: z.enum(ACTIVITY_TYPES),
  difficulty: z.enum(DIFFICULTIES).nullable(),
  startsAt: instant,
  endsAt: instant.nullable(),
  organizer: z.string(),
  distanceKm: z.number().nullable(),
  elevationGainM: z.number().int().nullable(),
  coverImageUrl: z.string().nullable(),
  /** "Author, licence" for the title of a small cover; the event page carries the full credit. */
  coverCredit: z.string().nullable(),
  /** The regular adult price, 0 when free; both null when the source names none. */
  priceAmount: z.number().nonnegative().nullable(),
  priceCurrency: z.string().nullable(),
});

const feedPageSchema = z.object({
  items: z.array(eventCardSchema),
  page: z.number().int(),
  size: z.number().int(),
  hasNext: z.boolean(),
});

/**
 * A link beside an event. The service has already refused anything but https;
 * it is checked again here because this value goes straight into an href. An
 * unknown network, one the service learned before this app did, is drawn as a
 * plain link rather than failing the page.
 */
const eventLinkSchema = z.object({
  network: z.enum(["TELEGRAM", "INSTAGRAM", "WHATSAPP", "FACEBOOK", "OTHER"]).catch("OTHER"),
  url: z.url({ protocol: /^https$/ }),
});

const eventPageSchema = z.object({
  id: z.number().int(),
  slug: z.string(),
  status: z.enum(["PUBLISHED", "CANCELLED"]),
  name: z.string(),
  placeName: z.string(),
  description: z.string(),
  activityType: z.enum(ACTIVITY_TYPES),
  difficulty: z.enum(DIFFICULTIES).nullable(),
  startsAt: instant,
  endsAt: instant.nullable(),
  passesAt: instant,
  latitude: z.number(),
  longitude: z.number(),
  organizer: z.string(),
  sourceUrl: z.string().nullable(),
  sourceCheckedAt: instant.nullable(),
  distanceKm: z.number().nullable(),
  elevationGainM: z.number().int().nullable(),
  coverImageUrl: z.string().nullable(),
  coverCredit: z
    .object({ author: z.string(), license: z.string(), licenseUrl: z.url({ protocol: /^https$/ }), sourceUrl: z.url({ protocol: /^https$/ }) })
    .nullable(),
  /** The regular adult price, 0 when free; null when the source names none. */
  price: z.object({ amount: z.number().nonnegative(), currency: z.string().regex(/^[A-Z]{3}$/) }).nullable(),
  eventLinks: z.array(eventLinkSchema),
  communityLinks: z.array(eventLinkSchema),
  updatedAt: instant,
});

const eventPointSchema = z.object({
  slug: z.string(),
  name: z.string(),
  placeName: z.string(),
  activityType: z.enum(ACTIVITY_TYPES),
  startsAt: instant,
  endsAt: instant.nullable(),
  coverImageUrl: z.string().nullable(),
  /** "Author, licence" for the title of a small cover; the event page carries the full credit. */
  coverCredit: z.string().nullable(),
  priceAmount: z.number().nonnegative().nullable(),
  priceCurrency: z.string().nullable(),
  latitude: z.number(),
  longitude: z.number(),
});

export const pointsPageSchema = z.object({
  items: z.array(eventPointSchema),
  truncated: z.boolean(),
});

export type EventCard = z.infer<typeof eventCardSchema>;
export type FeedPage = z.infer<typeof feedPageSchema>;
export type EventPage = z.infer<typeof eventPageSchema>;
export type EventPoint = z.infer<typeof eventPointSchema>;
export type PointsPage = z.infer<typeof pointsPageSchema>;

/**
 * Upcoming published events. {@code page} is one-based, as in the address bar.
 * The filter is part of the cache key, so each combination is cached on its
 * own and all of them drop together with the feed tag.
 */
export async function getFeed(locale: Locale, page: number, filter: FeedFilter = EMPTY_FILTER): Promise<FeedPage> {
  "use cache";
  cacheTag(TAGS.feed);
  cacheLife("hours");

  const url = new URL("/v1/events", eventServiceUrl());
  filterToParams(filter).forEach((value, key) => url.searchParams.append(key, value));
  url.searchParams.set("locale", locale);
  url.searchParams.set("page", String(page - 1));
  url.searchParams.set("size", String(FEED_PAGE_SIZE));

  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`event service answered ${response.status} for the feed`);
  }
  return feedPageSchema.parse(await response.json());
}

/** One event, or null when there is none to show (unknown, or a draft). */
export async function getEvent(slug: string, locale: Locale): Promise<EventPage | null> {
  "use cache";
  cacheTag(TAGS.event(slug));
  cacheLife("hours");

  const url = new URL(`/v1/events/${encodeURIComponent(slug)}`, eventServiceUrl());
  url.searchParams.set("locale", locale);

  const response = await fetch(url);
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`event service answered ${response.status} for ${slug}`);
  }
  return eventPageSchema.parse(await response.json());
}
