import "server-only";

import { revalidateTag } from "next/cache";
import { z } from "zod";

import { eventServiceUrl } from "@/lib/env";
import { TAGS } from "@/lib/events-api";
import { LOCALES } from "@/lib/locales";
import { ACTIVITY_TYPES, DIFFICULTIES } from "@/lib/vocabulary";

/**
 * The event service's write side for signed-in people (SPEC.md §18.2): own
 * events and moderation. Every call carries the person's elvix JWT, which the
 * service verifies itself; nothing here decides who may do what.
 */

const instant = z.iso.datetime({ offset: true });

export const ownEventSchema = z.object({
  slug: z.string(),
  status: z.enum(["DRAFT", "PUBLISHED", "CANCELLED", "SUSPENDED"]),
  suspensionReason: z.string().nullable(),
  locale: z.enum(LOCALES),
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
  distanceKm: z.number().nullable(),
  elevationGainM: z.number().int().nullable(),
  maxParticipants: z.number().int().nullable(),
  eventLinks: z.array(z.string()),
  communityLinks: z.array(z.string()),
  publishedAt: instant.nullable(),
  updatedAt: instant,
  communitySlug: z.string().nullable(),
  organizer: z.string(),
  // The organiser's own picture; null while the activity's stand-in shows.
  coverImageUrl: z.string().nullish().transform((value) => value ?? null),
});

export type OwnEvent = z.infer<typeof ownEventSchema>;

export const openReportSchema = z.object({
  id: z.number().int(),
  reason: z.enum(["SPAM", "MISLEADING", "DANGEROUS", "ILLEGAL", "PERSONAL_DATA", "OTHER"]),
  details: z.string(),
  createdAt: instant,
  slug: z.string(),
  kind: z.enum(["EXTERNAL", "OWN"]),
  eventStatus: z.string(),
  eventName: z.string(),
  organizer: z.string(),
  // The organiser's own picture, which moderation may take down alone.
  uploadedCover: z.string().nullish().transform((value) => value ?? null),
});

export type OpenReport = z.infer<typeof openReportSchema>;

/** The refusal codes the service sends; anything else reads as GENERIC. */
export const ERROR_CODES = [
  "TERMS_REQUIRED",
  "DISPLAY_NAME_REQUIRED",
  "LIMIT_PER_DAY",
  "LIMIT_UPCOMING",
  "IN_THE_PAST",
  "WRONG_STATE",
  "NOT_YOURS",
  "NOT_FOUND",
  "INVALID",
  "NO_FILE",
  "TOO_LARGE",
  "NOT_AN_IMAGE",
  "UNAVAILABLE",
  "GENERIC",
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

export function errorCode(value: unknown): ErrorCode {
  return typeof value === "string" && (ERROR_CODES as readonly string[]).includes(value) ? (value as ErrorCode) : "GENERIC";
}

export async function callEventService(
  method: "GET" | "POST" | "PUT",
  path: string,
  jwt: string | null,
  body?: unknown,
  headers: Record<string, string> = {},
): Promise<Response | null> {
  return fetch(new URL(path, eventServiceUrl()), {
    method,
    headers: {
      ...(jwt ? { Authorization: `Bearer ${jwt}` } : {}),
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
    signal: AbortSignal.timeout(10000),
  }).catch(() => null);
}

export async function listMine(jwt: string): Promise<OwnEvent[] | null> {
  const response = await callEventService("GET", "/v1/own/events", jwt);
  const parsed = z.array(ownEventSchema).safeParse(response?.ok ? await response.json().catch(() => null) : null);
  return parsed.success ? parsed.data : null;
}

export async function getMine(jwt: string, slug: string): Promise<OwnEvent | null> {
  const response = await callEventService("GET", `/v1/own/events/${encodeURIComponent(slug)}`, jwt);
  const parsed = ownEventSchema.safeParse(response?.ok ? await response.json().catch(() => null) : null);
  return parsed.success ? parsed.data : null;
}

export async function openReports(jwt: string): Promise<OpenReport[] | null> {
  const response = await callEventService("GET", "/v1/moderation/reports", jwt);
  const parsed = z.array(openReportSchema).safeParse(response?.ok ? await response.json().catch(() => null) : null);
  return parsed.success ? parsed.data : null;
}

/**
 * A write changed what the public sees: the feed and the event's own page drop
 * their cached render. A draft change touches neither, but telling apart costs
 * more than the extra render, so every successful write calls this.
 */
export function publicChanged(slug: string) {
  revalidateTag(TAGS.feed, { expire: 0 });
  revalidateTag(TAGS.event(slug), { expire: 0 });
}

export function moderates(roles: string[]): boolean {
  return roles.includes("moderator") || roles.includes("superuser");
}

/**
 * The editor's answer: {slug, status} on success (and the public renders
 * dropped), {code, field} on refusal, so the form keeps what was typed and says
 * what to fix.
 */
export async function answer(response: Response | null): Promise<Response> {
  if (!response) {
    return Response.json({ code: "UNAVAILABLE" }, { status: 503 });
  }
  const json = await response.json().catch(() => null);
  if (!response.ok) {
    // Spring's own validation failure carries no code; it is a form problem all the same.
    const code = response.status === 400 ? "INVALID" : errorCode(json?.code);
    return Response.json({ code, field: typeof json?.field === "string" ? json.field : null }, { status: response.status });
  }
  const event = ownEventSchema.safeParse(json);
  if (!event.success) {
    return Response.json({ code: "GENERIC" }, { status: 502 });
  }
  publicChanged(event.data.slug);
  return Response.json({ slug: event.data.slug, status: event.data.status });
}
