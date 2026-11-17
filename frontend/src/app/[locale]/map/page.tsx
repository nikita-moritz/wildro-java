import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { EventMapLoader } from "@/components/event-map-loader";
import { FiltersPanel } from "@/components/filters-panel";
import { LegalIsland } from "@/components/legal-island";
import { MapConsent } from "@/components/map-consent";
import { getDictionary } from "@/lib/dictionaries";
import { mapboxToken } from "@/lib/env";
import { filterToParams, parseFilter } from "@/lib/feed-filter";
import { activeFilterCount, filtersText } from "@/lib/filters-text";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { alternatesFor } from "@/lib/seo";

/** Matches the service's cap per viewport (EventReader.MAX_POINTS). */
const MAX_POINTS = 300;

/**
 * The working version's full-screen map with the Filters button in the corner.
 * An interactive page with nothing in its HTML for a crawler to read, so it is
 * kept out of the index and the sitemap; the list is the indexable view of the
 * same events. "Near me" is left out here: the map is already about where.
 *
 * Laid out as islands over a full-screen map: the list on the left, the
 * filters on top. No footer; the imprint links sit under the list instead,
 * because they have to be reachable from every page (§ 5 DDG).
 */
export const instant = false;

export async function generateMetadata({ params }: PageProps<"/[locale]/map">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  return {
    title: t.map.title,
    description: t.map.description,
    alternates: alternatesFor((each) => routes.map(each), locale),
    robots: { index: false, follow: true },
  };
}

export default async function MapPage({ params, searchParams }: PageProps<"/[locale]/map">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const filter = { ...parseFilter(await searchParams), near: null };
  const accessToken = mapboxToken();

  return (
    // Pulled up under the header (h-16), whose islands then float over the map.
    <main className="relative -mt-16 flex h-dvh min-h-112 flex-col overflow-hidden">
      <h1 className="sr-only">{t.map.title}</h1>
      <div className="relative min-h-0 flex-1">
        {accessToken ? (
          <MapConsent
            privacyHref={routes.privacy(locale)}
            t={{
              title: t.map.consentTitle,
              body: t.map.consentBody,
              button: t.map.consentButton,
              privacyLink: t.map.consentPrivacyLink,
            }}
          >
            <EventMapLoader
              locale={locale}
              filterQuery={filterToParams(filter).toString()}
              eventHref={routes.event(locale, "{slug}")}
              accessToken={accessToken}
              filters={
                <FiltersPanel
                  action={routes.map(locale)}
                  locale={locale}
                  filter={filter}
                  activeCount={activeFilterCount(filter)}
                  t={filtersText(t)}
                  allowNearMe={false}
                  inline
                />
              }
              t={{
                emptyView: t.map.emptyView,
                tooMany: t.map.tooMany(MAX_POINTS),
                openEvent: t.map.openEvent,
                inView: t.map.inView,
                listLabel: t.map.listLabel,
                search: t.map.search,
                noMatch: t.map.noMatch,
                activityLabels: t.activity,
                priceLabels: t.price,
              }}
            />
          </MapConsent>
        ) : (
          <p className="p-6 text-sm text-muted-foreground">{t.map.unavailable}</p>
        )}
      </div>

      {/*
        No footer on the map, but the imprint stays one click away from every page (§ 5 DDG):
        under the list island on a wide screen, a line under the list on a phone.
      */}
      <LegalIsland
        locale={locale}
        t={t}
        className="z-20 shrink-0 border-t bg-card px-5 py-2 md:legal-island md:absolute md:bottom-3 md:left-3 md:w-96"
      />
    </main>
  );
}
