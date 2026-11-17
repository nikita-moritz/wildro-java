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
