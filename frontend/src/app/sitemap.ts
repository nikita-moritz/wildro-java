import type { MetadataRoute } from "next";
import { connection } from "next/server";

import { listCommunities } from "@/lib/communities";
import { siteUrl } from "@/lib/env";
import { getFeed, MAX_FEED_PAGE } from "@/lib/events-api";
import { DEFAULT_LOCALE, LOCALES, type Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";

/** The service's own bound on the group list's depth (CommunityController, pages 0 to 50). */
const MAX_COMMUNITY_PAGE = 51;

/**
 * Everything indexable, each address in every language with its alternates:
 * the fixed pages, every upcoming event and every open group. Read when asked,
 * never at build time (the build has no services); the lists come from the same
 * cached reads as the pages, so a crawler's visit costs the services nothing new.
 * A service that cannot answer leaves its part out rather than failing the file.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  await connection();
  const site = siteUrl();
  const [events, communities] = await Promise.all([upcomingEventSlugs().catch(() => []), communitySlugs().catch(() => [])]);

  // Only what is indexed: the map, the legal pages and sign-in carry noindex, and a sitemap naming them would contradict it.
  const pages: Array<(locale: Locale) => string> = [
    routes.home,
    (locale) => routes.feed(locale),
    (locale) => routes.communities(locale),
    routes.brand,
    ...events.map((slug) => (locale: Locale) => routes.event(locale, slug)),
    ...communities.map((slug) => (locale: Locale) => routes.community(locale, slug)),
  ];
  return pages.flatMap((path) =>
    LOCALES.map((locale) => ({
      url: `${site}${path(locale)}`,
      alternates: { languages: Object.fromEntries(LOCALES.map((each) => [each, `${site}${path(each)}`])) },
    })),
  );
}

/** Slugs are the same in every language, so one language's feed names them all. */
async function upcomingEventSlugs(): Promise<string[]> {
  const slugs: string[] = [];
  for (let page = 1; page <= MAX_FEED_PAGE; page++) {
    const feed = await getFeed(DEFAULT_LOCALE, page);
    slugs.push(...feed.items.map((event) => event.slug));
    if (!feed.hasNext) {
      break;
    }
  }
  return slugs;
}

async function communitySlugs(): Promise<string[]> {
  const slugs: string[] = [];
  for (let page = 1; page <= MAX_COMMUNITY_PAGE; page++) {
    const list = await listCommunities(page);
    slugs.push(...list.items.map((community) => community.slug));
    if (!list.hasNext) {
      break;
    }
  }
  return slugs;
}
