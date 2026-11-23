import { ArrowRight, Calendar, CalendarPlus, ExternalLink, Footprints, MapPin, Share2, SlidersHorizontal, Users } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { connection } from "next/server";
import { Suspense } from "react";

import { EventTeaser } from "@/components/event-teaser";
import { SiteFooter } from "@/components/site-footer";
import { Button } from "@/components/ui/button";
import { getDictionary, type Dictionary } from "@/lib/dictionaries";
import { brand } from "@/lib/env";
import { getFeed } from "@/lib/events-api";
import { isLocale, type Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { alternatesFor } from "@/lib/seo";

/**
 * The landing page: find an event (hero, "coming up next", "how it works"), then
 * make your own (create an event, start a group, share them). Signing in is
 * asked on the way, by the pages these buttons lead to.
 */

const PREVIEW_COUNT = 6;

const STEP_ICONS = [MapPin, SlidersHorizontal, ExternalLink, Footprints];
const ORGANISE_ICONS = [CalendarPlus, Users, Share2];

export async function generateMetadata({ params }: PageProps<"/[locale]">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  return {
    // Topic and region first, for search; the brand comes last, as on every other page.
    title: { absolute: `${t.site.homeTitle} · ${brand().name}` },
    alternates: alternatesFor(routes.home, locale),
  };
}

export default async function HomePage({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const l = t.home;

  return (
    <>
      <main className="flex-1">
        <section className="relative overflow-hidden border-b">
          <div className="relative mx-auto w-full max-w-5xl px-4 pt-16 pb-16 sm:pt-24">
            <span className="inline-flex items-center gap-2 rounded-full border bg-card/60 px-3 py-1 text-xs font-medium text-muted-foreground backdrop-blur">
              <span className="relative flex h-2 w-2">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary/60 motion-reduce:animate-none" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-primary" />
              </span>
              {l.kicker}
            </span>

            <h1 className="mt-6 max-w-3xl text-4xl leading-[1.08] font-bold tracking-tight sm:text-6xl">
              {l.title1} <span className="text-primary">{l.title2}</span>
            </h1>

            <p className="mt-5 max-w-xl text-base leading-relaxed text-muted-foreground sm:text-lg">{l.subtitle}</p>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Button asChild size="lg" className="h-12 gap-2 px-6 text-base">
                <Link href={routes.map(locale)}>
                  {l.primaryCta}
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </Button>
              <Button asChild size="lg" variant="outline" className="h-12 gap-2 px-6 text-base">
                <Link href={routes.newEvent(locale)}>
                  <CalendarPlus className="h-4 w-4" />
                  {l.secondaryCta}
                </Link>
              </Button>
            </div>
          </div>
        </section>

        <section className="mx-auto w-full max-w-5xl px-4 py-16">
          <div className="mb-6 flex items-end justify-between gap-4">
            <div>
              <h2 className="text-2xl font-bold tracking-tight">{l.upcomingTitle}</h2>
              <p className="mt-1 text-sm text-muted-foreground">{l.upcomingSubtitle}</p>
            </div>
            <Link
              href={routes.feed(locale)}
              className="hidden shrink-0 items-center gap-1.5 text-sm font-medium text-primary hover:underline sm:flex"
            >
              {l.upcomingCta}
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>

          <Suspense fallback={<TeaserSkeleton />}>
            <UpcomingTeasers locale={locale} t={t} />
          </Suspense>

          <Link
            href={routes.feed(locale)}
            className="mt-6 flex items-center justify-center gap-1.5 text-sm font-medium text-primary hover:underline sm:hidden"
          >
            {l.upcomingCta}
            <ArrowRight className="h-4 w-4" />
          </Link>
        </section>

        <section className="border-y bg-muted/30">
          <div className="mx-auto w-full max-w-5xl px-4 py-16">
            <h2 className="text-2xl font-bold tracking-tight">{l.featuresTitle}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{l.featuresSubtitle}</p>
            <ol className="mt-8 grid gap-x-8 gap-y-6 sm:grid-cols-2 lg:grid-cols-4">
              {l.features.map((feature, index) => {
                const Icon = STEP_ICONS[index] ?? MapPin;
                return (
                  <li key={feature.title} className="relative pl-11">
                    <span className="absolute top-0 left-0 flex h-8 w-8 items-center justify-center rounded-lg border bg-card">
                      <Icon className="h-4 w-4 text-primary" />
                    </span>
                    <span className="text-xs font-semibold text-primary/70 tabular-nums">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                    <p className="mt-0.5 font-semibold">{feature.title}</p>
                    <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{feature.description}</p>
                  </li>
                );
              })}
            </ol>
          </div>
        </section>

        <section className="mx-auto w-full max-w-5xl px-4 py-16">
          <h2 className="text-2xl font-bold tracking-tight">{l.organiseTitle}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{l.organiseSubtitle}</p>
          <ul className="mt-8 grid gap-4 sm:grid-cols-3">
            {l.organise.map((item, index) => {
              const Icon = ORGANISE_ICONS[index] ?? CalendarPlus;
              return (
                <li key={item.title} className="rounded-2xl border bg-card p-5">
                  <Icon className="h-5 w-5 text-primary" />
                  <p className="mt-3 font-semibold">{item.title}</p>
                  <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{item.description}</p>
                </li>
              );
            })}
          </ul>
          <div className="mt-8 flex flex-wrap gap-3">
            <Button asChild className="h-11 px-5">
              <Link href={routes.newEvent(locale)}>{l.secondaryCta}</Link>
            </Button>
            <Button asChild variant="outline" className="h-11 px-5">
              <Link href={routes.newCommunity(locale)}>{l.organiseGroup}</Link>
            </Button>
          </div>
        </section>
      </main>
      <SiteFooter locale={locale} t={t} />
    </>
  );
}

async function UpcomingTeasers({ locale, t }: { locale: Locale; t: Dictionary }) {
  // At request time, not at build: the machine that builds has no event service
  // to ask, and the list is cached per tag once asked. Past events must also
  // drop out as time passes, which a list frozen at build would not do.
  await connection();
  const feed = await getFeed(locale, 1);
  const events = feed.items.slice(0, PREVIEW_COUNT);

  if (events.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed py-14 text-center">
        <Calendar className="h-8 w-8 text-muted-foreground/40" />
        <p className="font-medium">{t.home.emptyTitle}</p>
        <p className="max-w-xs text-sm text-muted-foreground">{t.home.emptySubtitle}</p>
      </div>
    );
  }
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {events.map((event) => (
        <EventTeaser key={event.slug} event={event} locale={locale} t={t} />
      ))}
    </div>
  );
}

function TeaserSkeleton() {
  return (
    <div aria-hidden="true" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: PREVIEW_COUNT }, (_, index) => (
        <div key={index} className="h-72 animate-pulse rounded-2xl bg-muted" />
      ))}
    </div>
  );
}
