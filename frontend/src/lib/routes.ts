import type { Locale } from "@/lib/locales";

/**
 * The shape of every public address, written once. Links, canonical tags,
 * hreflang alternates and the sitemap are all built from here, so an address
 * cannot be spelt two ways. Decided in SPEC.md §0, closed question 12; an
 * indexed address never moves, so change nothing here without that decision.
 *
 * Paths are English whatever the page language, per house rule. The event page
 * is singular (/event/) so it can never collide with the city pages of stage
 * two, which live under /events/{city}.
 *
 * {@code query} carries filters. A filtered address is never canonical and
 * never indexed: robots.ts closes the filter parameters to crawlers.
 */
export const routes = {
  home: (locale: Locale) => `/${locale}`,
  feed: (locale: Locale, page = 1, query?: URLSearchParams) => withQuery(`/${locale}/events`, page, query),
  event: (locale: Locale, slug: string) => `/${locale}/event/${slug}`,
  map: (locale: Locale, query?: URLSearchParams) => withQuery(`/${locale}/map`, 1, query),
  legalNotice: (locale: Locale) => `/${locale}/legal-notice`,
  privacy: (locale: Locale) => `/${locale}/privacy`,
  // Not indexed and not in the sitemap: personal pages, closed in robots.ts.
  // next: where to return after signing in, a path of this site (lib/signed-in-page.ts checks it).
  signIn: (locale: Locale, next?: string) => `/${locale}/sign-in${next ? `?next=${encodeURIComponent(next)}` : ""}`,
  account: (locale: Locale) => `/${locale}/account`,
  myEvents: (locale: Locale) => `/${locale}/account/events`,
  /** A blank event, a copy of one's own (`from`), or one for a group one runs (`group`). */
  newEvent: (locale: Locale, start: { from?: string; group?: string } = {}) => {
    const query = new URLSearchParams(Object.entries(start).filter(([, value]) => value) as [string, string][]).toString();
    return `/${locale}/account/events/new${query ? `?${query}` : ""}`;
  },
  editEvent: (locale: Locale, slug: string) => `/${locale}/account/events/${slug}`,
  moderation: (locale: Locale) => `/${locale}/moderation`,
  moderationCommunities: (locale: Locale) => `/${locale}/moderation/communities`,
  // Public and indexable: community pages are search traffic (SPEC.md §18.3).
  // q, a name or a town, is a search: such an address is closed to crawlers like the filters.
  communities: (locale: Locale, page = 1, q?: string | null) =>
    withQuery(`/${locale}/communities`, page, q ? new URLSearchParams({ q }) : undefined),
  community: (locale: Locale, slug: string) => `/${locale}/community/${slug}`,
  myCommunities: (locale: Locale) => `/${locale}/account/communities`,
  newCommunity: (locale: Locale) => `/${locale}/account/communities/new`,
  manageCommunity: (locale: Locale, slug: string) => `/${locale}/account/communities/${slug}`,
  // Public and indexable, like the other legal pages.
  terms: (locale: Locale) => `/${locale}/terms`,
  brand: (locale: Locale) => `/${locale}/brand`,
} as const;

function withQuery(path: string, page: number, query?: URLSearchParams): string {
  const params = new URLSearchParams(query);
  params.delete("page");
  if (page > 1) {
    params.set("page", String(page));
  }
  const search = params.toString();
  return search ? `${path}?${search}` : path;
}
