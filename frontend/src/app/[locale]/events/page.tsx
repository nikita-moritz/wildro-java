import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { eventCardText } from "@/components/event-list-item";
import { FeedList } from "@/components/feed-list";
import { FeedPageLink } from "@/components/feed-pagination";
import { FeedViewToggle } from "@/components/feed-view-toggle";
import { FiltersPanel } from "@/components/filters-panel";
import { LegalIsland } from "@/components/legal-island";
import { SearchBox } from "@/components/search-box";
import { SiteFooter } from "@/components/site-footer";
import { getDictionary } from "@/lib/dictionaries";
import { getFeed } from "@/lib/events-api";
import { filterToParams, isEmptyFilter, parseFilter } from "@/lib/feed-filter";
import { parseFeedPage } from "@/lib/feed-page";
import { activeFilterCount, filtersText } from "@/lib/filters-text";
import { searchWords } from "@/lib/highlight";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { alternatesFor } from "@/lib/seo";

/**
 * The events list: search, view and filters in a bar that stays under the
 * header, then the list, which grows while scrolling (SPEC.md §0.3.2).
 * Blocking, like the event page: a page number that does not exist must answer
 * a real 404 before any byte is sent.
 *
 * A filtered list is never canonical. It names the unfiltered page of the same
 * number as canonical, and robots.ts keeps crawlers off filter parameters.
 */
export const instant = false;

export async function generateMetadata({ params, searchParams }: PageProps<"/[locale]/events">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const page = parseFeedPage((await searchParams).page);
  if (page === null) {
    notFound();
  }
  const t = getDictionary(locale);
  return {
    title: page > 1 ? t.feed.pageTitle(page) : t.feed.metaTitle,
    description: t.feed.description,
    alternates: alternatesFor((each) => routes.feed(each, page), locale),
  };
}

export default async function FeedPage({ params, searchParams }: PageProps<"/[locale]/events">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const query = await searchParams;
  const page = parseFeedPage(query.page);
  if (page === null) {
    notFound();
  }
  const filter = parseFilter(query);
  const feed = await getFeed(locale, page, filter);
  // Page one may be empty and says so; any later page that is empty is past the end.
  if (page > 1 && feed.items.length === 0) {
    notFound();
  }
  const t = getDictionary(locale);
  const filterQuery = filterToParams(filter);

  return (
    <>
      <main className="flex-1">
        {/* Tiles use the whole width of the screen (owner's call, 2026-11-03); the list keeps its column. */}
        <div className="mx-auto flex max-w-2xl flex-col gap-3 px-4 pt-4 pb-6 tiles-chosen:max-w-none">
          {/* Above the bar's band (z-40) and below the header (z-50): in place, the band never covers it. */}
          <h1 className="relative z-45 text-lg font-semibold">{t.feed.title}</h1>

          {/*
            Stays under the header while the list scrolls (top-16 is the header's
            height), on a solid band that also covers the header's strip above it
            (before:), so the events pass out of sight beneath it instead of showing
            between the islands; they fade out at its lower edge (after:). Owner's
            call, 2026-11-03: with see-through gaps the search box got lost in them.
          */}
          <div className="sticky top-16 z-40 -mx-4 flex items-center gap-2 bg-background px-4 py-2 before:pointer-events-none before:absolute before:inset-x-0 before:bottom-full before:h-16 before:bg-background after:pointer-events-none after:absolute after:inset-x-0 after:top-full after:h-3 after:bg-linear-to-b after:from-background after:to-transparent">
            {/* Over the full-width tiles the box keeps a readable width; view and filters go to the right edge. */}
            <div className="min-w-0 flex-1 tiles-chosen:max-w-2xl">
              <SearchBox
                action={routes.feed(locale)}
                value={filter.q}
                others={filterToParams({ ...filter, q: null }).toString()}
                t={t.feed.search}
              />
            </div>
            <div className="ml-auto flex shrink-0 items-center gap-2">
              <FeedViewToggle t={t.feed.view} />
              <FiltersPanel
                action={routes.feed(locale)}
                locale={locale}
                filter={filter}
                activeCount={activeFilterCount(filter)}
                t={filtersText(t)}
              />
            </div>
          </div>

          {/*
            The footer below keeps moving away while the list grows, so the imprint
            floats as on the map (§ 5 DDG). Before the list in the markup, so the Tab
            key reaches it: after it, a growing list would keep it out of reach.
          */}
          <LegalIsland
            locale={locale}
            t={t}
            // Beside the column only where there is room for it; narrower screens have the links in the menu.
            className="legal-island fixed bottom-3 left-3 z-30 hidden lg:flex lg:flex-col lg:gap-1 xl:flex-row xl:gap-4"
          />

          {page > 1 && (
            <FeedPageLink href={routes.feed(locale, page - 1, filterQuery)} rel="prev" className="self-start">
              {t.feed.previous}
            </FeedPageLink>
          )}

          {feed.items.length === 0 ? (
            <p className="py-16 text-center text-sm text-muted-foreground">
              {isEmptyFilter(filter) ? t.feed.empty : t.feed.filteredEmpty}
            </p>
          ) : (
            <FeedList
              key={`${filterQuery}:${page}`}
              locale={locale}
              initialItems={feed.items}
              page={page}
              hasNext={feed.hasNext}
              query={filterQuery.toString()}
              words={searchWords(filter.q)}
              text={eventCardText(t)}
              t={{ next: t.feed.next, loading: t.feed.loading, loadFailed: t.feed.loadFailed, retry: t.feed.retry }}
            />
          )}
        </div>
      </main>
      <SiteFooter locale={locale} t={t} variant="compact" />
    </>
  );
}
