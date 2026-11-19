import "server-only";

import { cacheLife, cacheTag, revalidateTag } from "next/cache";
import { z } from "zod";

import { communityServiceUrl, eventServiceUrl } from "@/lib/env";
import { eventCardSchema, TAGS } from "@/lib/events-api";
import { LOCALES, type Locale } from "@/lib/locales";

/**
 * The community service as this frontend sees it (SPEC.md §18.3). Public reads
 * are cached and tagged like events; everything personal is read per request
 * with the person's JWT and never cached.
 */

export const COMMUNITY_TAGS = {
  list: "communities:list",
  page: (slug: string) => `community:${slug}`,
} as const;

const roleSchema = z.enum(["OWNER", "ADMIN", "MEMBER"]);
export type Role = z.infer<typeof roleSchema>;

const summarySchema = z.object({
  slug: z.string(),
  name: z.string(),
  area: z.string(),
  locale: z.enum(LOCALES),
  memberCount: z.number().int(),
});

const listSchema = z.object({ items: z.array(summarySchema), page: z.number().int(), hasNext: z.boolean() });

export const communityPageSchema = z.object({
  id: z.number().int(),
  slug: z.string(),
  name: z.string(),
  description: z.string(),
  area: z.string(),
  locale: z.enum(LOCALES),
  status: z.enum(["ACTIVE", "CLOSED"]),
  links: z.array(z.string()),
  memberCount: z.number().int(),
  createdAt: z.string(),
  closedAt: z.string().nullable(),
});

export type CommunityPage = z.infer<typeof communityPageSchema>;

export const standingSchema = z.object({
  id: z.number().int(),
  slug: z.string(),
  name: z.string(),
  status: z.string(),
  role: roleSchema.nullable(),
});

export type Standing = z.infer<typeof standingSchema>;

export const memberSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
  role: roleSchema,
  joinedAt: z.string(),
});

export type Member = z.infer<typeof memberSchema>;

function service(path: string): URL {
  const base = communityServiceUrl();
  if (!base) {
    throw new Error("COMMUNITY_SERVICE_URL is not set");
  }
  return new URL(path, base);
}

export async function listCommunities(page: number): Promise<z.infer<typeof listSchema>> {
  "use cache";
  cacheTag(COMMUNITY_TAGS.list);
  cacheLife("hours");
  const response = await fetch(service(`/v1/communities?page=${page - 1}`));
  if (!response.ok) {
    throw new Error(`community service answered ${response.status} for the list`);
  }
  return listSchema.parse(await response.json());
}

/** One community, or null when there is none to show (unknown, or taken down). */
export async function getCommunity(slug: string): Promise<CommunityPage | null> {
  "use cache";
  cacheTag(COMMUNITY_TAGS.page(slug));
  cacheLife("hours");
  const response = await fetch(service(`/v1/communities/${encodeURIComponent(slug)}`));
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`community service answered ${response.status} for ${slug}`);
  }
  return communityPageSchema.parse(await response.json());
}

/** The community's upcoming events, cached with the feed: a write that changes the feed changes these. */
export async function getCommunityEvents(communityId: number, locale: Locale) {
  "use cache";
  cacheTag(TAGS.feed);
  cacheLife("hours");
  const url = new URL(`/v1/events/community/${communityId}`, eventServiceUrl());
  url.searchParams.set("locale", locale);
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`event service answered ${response.status} for community ${communityId}`);
  }
  return z.array(eventCardSchema).parse(await response.json());
}

export async function callCommunityService(
  method: "GET" | "POST" | "PUT",
  path: string,
  jwt: string,
  body?: unknown,
): Promise<Response | null> {
  const base = communityServiceUrl();
  if (!base) {
    return null;
  }
  return fetch(new URL(path, base), {
    method,
    headers: {
      Authorization: `Bearer ${jwt}`,
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
    signal: AbortSignal.timeout(10000),
  }).catch(() => null);
}

export async function myCommunities(jwt: string): Promise<Standing[] | null> {
  const response = await callCommunityService("GET", "/v1/me/communities", jwt);
  const parsed = z.array(standingSchema).safeParse(response?.ok ? await response.json().catch(() => null) : null);
  return parsed.success ? parsed.data : null;
}

export async function standingIn(jwt: string, slug: string): Promise<Standing | null> {
  const response = await callCommunityService("GET", `/v1/communities/${encodeURIComponent(slug)}/me`, jwt);
  const parsed = standingSchema.safeParse(response?.ok ? await response.json().catch(() => null) : null);
  return parsed.success ? parsed.data : null;
}

export async function membersOf(jwt: string, slug: string): Promise<Member[] | null> {
  const response = await callCommunityService("GET", `/v1/communities/${encodeURIComponent(slug)}/members`, jwt);
  const parsed = z.array(memberSchema).safeParse(response?.ok ? await response.json().catch(() => null) : null);
  return parsed.success ? parsed.data : null;
}

export const moderatedSchema = z.object({
  slug: z.string(),
  name: z.string(),
  area: z.string(),
  status: z.enum(["ACTIVE", "SUSPENDED"]),
  suspensionReason: z.string().nullable(),
  memberCount: z.number().int(),
  createdAt: z.string(),
  // Filled for a superuser only: the people ownership can pass to.
  leaders: z.array(memberSchema),
});

export type Moderated = z.infer<typeof moderatedSchema>;

/** Communities moderation can act on, suspended first; null when the service did not answer or refused. */
export async function moderatedCommunities(jwt: string, query: string): Promise<Moderated[] | null> {
  const response = await callCommunityService("GET", `/v1/moderation/communities?q=${encodeURIComponent(query)}`, jwt);
  const parsed = z.array(moderatedSchema).safeParse(response?.ok ? await response.json().catch(() => null) : null);
  return parsed.success ? parsed.data : null;
}

/** A community changed: its page and the list drop their cached render; a close also changes the feed. */
export function communityChanged(slug: string, feedToo = false) {
  revalidateTag(COMMUNITY_TAGS.page(slug), { expire: 0 });
  revalidateTag(COMMUNITY_TAGS.list, { expire: 0 });
  if (feedToo) {
    revalidateTag(TAGS.feed, { expire: 0 });
  }
}

export function manages(role: Role | null | undefined): boolean {
  return role === "OWNER" || role === "ADMIN";
}
