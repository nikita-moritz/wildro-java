import {
  ArrowRight,
  Calendar,
  ExternalLink,
  Footprints,
  MapPin,
  Mountain,
  Navigation,
  TrendingUp,
  User,
} from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { ACTIVITY_ICON, DIFFICULTY_STYLE } from "@/components/activity";
import { BackLink } from "@/components/back-link";
import { PriceBadge } from "@/components/price-badge";
import { EventLinks } from "@/components/event-links";
import { ReportForm } from "@/components/report-form";
import { ShareBar } from "@/components/share/share-bar";
import { EventListItem, eventCardText } from "@/components/event-list-item";
import { SiteFooter } from "@/components/site-footer";
import { Button } from "@/components/ui/button";
import { communityShown } from "@/lib/communities";
import { getDictionary, type Dictionary } from "@/lib/dictionaries";
import { getEvent, getFeed, type EventPage } from "@/lib/events-api";
import { defaultCover } from "@/lib/covers";
import { formatDate, formatDay, formatNumber, formatTime, sameDay } from "@/lib/format";
import { isLocale, type Locale } from "@/lib/locales";
import { requestTime } from "@/lib/request-time";
import { routes } from "@/lib/routes";
import { absoluteUrl, alternatesFor, serialiseJsonLd } from "@/lib/seo";
import { googleCalendarUrl, shareText } from "@/lib/share";
import { cn } from "@/lib/utils";

/**
 * The working version's event page: back link, title, chips, cover, a card of
 * facts, the description, and one wide button at the bottom. What stage one
 * does not have is gone: joining and leaving, the places bar, the group chat,
 * the "log in for details" gate, and the report form, since stage one has no
 * network write path at all (SPEC.md §0.1). The wide button signs up with the
 * organiser at the source instead.
 *
 * Blocking, on purpose: title, canonical and hreflang land in <head> for every
 * client, and a missing event is a real 404 for crawlers.
 */
export const instant = false;

/** How many upcoming events a past page points to, so it is never an orphan (SPEC.md §0, question 4). */
const UPCOMING_ON_PAST_PAGE = 3;

/** Search engines cut descriptions around here; a cut at a word reads better than one mid-word. */
const DESCRIPTION_LENGTH = 155;

function excerpt(text: string): string {
  const flat = text.replace(/\s+/g, " ").trim();
  if (flat.length <= DESCRIPTION_LENGTH) {
    return flat;
  }
  const cut = flat.slice(0, DESCRIPTION_LENGTH);
  return `${cut.slice(0, cut.lastIndexOf(" "))}…`;
}

export async function generateMetadata({ params }: PageProps<"/[locale]/event/[slug]">): Promise<Metadata> {
  const { locale, slug } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const event = await getEvent(slug, locale);
  if (!event) {
    notFound();
  }
  // A link shared in a chat gets a picture either way: the event's own, or its activity's stand-in.
  const previewImage = event.coverImageUrl ?? defaultCover(event.activityType)?.large;
  return {
    title: event.name,
    description: excerpt(event.description),
    alternates: alternatesFor((each) => routes.event(each, slug), locale),
    openGraph: {
      type: "website",
      url: absoluteUrl(routes.event(locale, slug)),
      title: event.name,
      description: excerpt(event.description),
      ...(previewImage ? { images: [{ url: previewImage }] } : {}),
    },
  };
}

export default async function EventPageRoute({ params }: PageProps<"/[locale]/event/[slug]">) {
  const { locale, slug } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const event = await getEvent(slug, locale);
  if (!event) {
    notFound();
  }
  const t = getDictionary(locale);
  // A group taken down keeps its name on its events but loses the link, which
  // would lead to a missing page. Read from the group's cached page, so a
  // suspension or reinstatement changes it at once.
  const groupLinked = event.communitySlug ? await communityShown(event.communitySlug) : false;
  // Compared per request, never stored: the service sends the moment the event
  // passes, not a flag that would go stale inside a cached response.
  const passed = (await requestTime()) > Date.parse(event.passesAt);
  const cancelled = event.status === "CANCELLED";
  const pageUrl = absoluteUrl(routes.event(locale, event.slug));
  // What a post in a chat says about the event, in the page's language.
  const when = `${formatDate(event.startsAt, locale)}, ${formatTime(event.startsAt, locale)}`;
  const shareFacts = {
    name: event.name,
    when,
    placeName: event.placeName,
    facts: [
      t.activity[event.activityType],
      event.distanceKm === null ? null : `${formatNumber(event.distanceKm, locale)} km`,
      event.difficulty === null ? null : t.difficulty[event.difficulty],
    ]
      .filter(Boolean)
      .join(" · "),
    description: event.description,
    url: pageUrl,
  };
  const own = event.kind === "OWN";
  // An own event is written in one language and shown as written (SPEC.md §18.2, question 3).
  const foreign = own && event.originalLocale !== null && event.originalLocale !== locale;
  const textLang = foreign && event.originalLocale ? event.originalLocale : undefined;
  const ActivityIcon = ACTIVITY_ICON[event.activityType];
  const spansDays = event.endsAt !== null && !sameDay(event.startsAt, event.endsAt);
  const standIn = defaultCover(event.activityType);
  // Google's documented cross-platform link: on a phone it opens the Maps app, where the visitor
  // can start navigation to the meeting point. A plain link, so nothing reaches Google before the click.
  const mapUrl = `https://www.google.com/maps/search/?api=1&query=${event.latitude},${event.longitude}`;

  return (
    <>
      <main className="flex-1">
        <article className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-6">
          <script
            type="application/ld+json"
            dangerouslySetInnerHTML={{ __html: serialiseJsonLd(jsonLd(event, locale, t)) }}
          />

          <BackLink href={routes.feed(locale)} label={t.event.back} />

          <h1 lang={textLang} className="text-2xl leading-tight font-bold">
            {event.name}
          </h1>

          {cancelled && (
            <p role="status" className="rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm font-medium text-destructive">
              {t.event.cancelled}
            </p>
          )}
          {!cancelled && passed && (
            <p role="status" className="rounded-xl border bg-muted px-4 py-3 text-sm font-medium">
              {t.event.past}
            </p>
          )}

          {/* Only for what is still ahead: nobody shares or plans a past or cancelled outing. */}
          {!cancelled && !passed && (
            <ShareBar
              title={event.name}
              line={`${when} · ${event.placeName}`}
              url={pageUrl}
              texts={{
                whatsapp: shareText("whatsapp", shareFacts, t.share.more),
                telegram: shareText("telegram", shareFacts, t.share.more),
                facebook: shareText("facebook", shareFacts, t.share.more),
              }}
              googleUrl={googleCalendarUrl({ ...event, url: pageUrl })}
              icsUrl={`${routes.event(locale, event.slug)}/calendar.ics`}
              t={t.share}
            />
          )}

          <div className="flex flex-wrap gap-2">
            {own && <Chip className="border-primary/30 bg-primary/10 text-primary">{t.event.ownBadge}</Chip>}
            <Chip>
              <ActivityIcon className="h-3 w-3 text-muted-foreground" />
              {t.activity[event.activityType]}
            </Chip>
            {event.difficulty && (
              <Chip className={DIFFICULTY_STYLE[event.difficulty].chip}>
                <Mountain className="h-3 w-3" />
                {t.difficulty[event.difficulty]}
              </Chip>
            )}
            <PriceBadge
              amount={event.price?.amount ?? null}
              currency={event.price?.currency ?? null}
              locale={locale}
              labels={t.price}
              className="px-3 py-1 text-xs"
            />
            {event.distanceKm !== null && (
              <Chip>
                <Navigation className="h-3 w-3 text-muted-foreground" />
                {t.event.kilometres(formatNumber(event.distanceKm, locale))}
              </Chip>
            )}
            {event.elevationGainM !== null && (
              <Chip>
                <TrendingUp className="h-3 w-3 text-muted-foreground" />
                {t.event.metres(formatNumber(event.elevationGainM, locale, 0))}
              </Chip>
            )}
          </div>

          {event.coverImageUrl ? (
            <figure className="flex flex-col gap-1.5">
              <div className="overflow-hidden rounded-xl border">
                {/* A freely licensed photo in our own storage; already sized, so no optimiser. */}
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={event.coverImageUrl} alt="" width={1200} height={675} className="h-auto w-full" />
              </div>
              {/* The licence allows the photo only with its author and licence beside it. */}
              {event.coverCredit && (
                <figcaption className="text-xs text-muted-foreground">
                  {t.event.photo}: {event.coverCredit.author},{" "}
                  <a href={event.coverCredit.licenseUrl} target="_blank" rel="noopener noreferrer license" className="underline underline-offset-2 hover:text-foreground">
                    {event.coverCredit.license}
                  </a>
                  ,{" "}
                  <a href={event.coverCredit.sourceUrl} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2 hover:text-foreground">
                    {t.event.photoSource}
                  </a>
                  . {t.event.photoPlace}
                </figcaption>
              )}
            </figure>
          ) : (
            standIn && (
              <figure className="flex flex-col gap-1.5">
                <div className="overflow-hidden rounded-xl border">
                  {/* Decorative: it shows this kind of outing, not this event, which the caption says. */}
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={standIn.large}
                    srcSet={standIn.srcSet}
                    sizes="(min-width: 768px) 42rem, 100vw"
                    width={standIn.width}
                    height={standIn.height}
                    alt=""
                    className="h-auto w-full"
                  />
                </div>
                <figcaption className="text-xs text-muted-foreground">{t.event.stockPhoto}</figcaption>
              </figure>
            )
          )}

          <div className="flex flex-col gap-4 rounded-xl border bg-card p-5">
            <MetaRow icon={<Calendar className="h-4 w-4" />}>
              {spansDays && event.endsAt ? (
                <div>
                  <p className="font-medium">
                    {formatDate(event.startsAt, locale)}
                    <ArrowRight className="mx-1.5 inline h-3.5 w-3.5 text-muted-foreground" />
                    {formatDate(event.endsAt, locale)}
                  </p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {t.event.startTime}: {formatTime(event.startsAt, locale)}
                  </p>
                </div>
              ) : (
                <div>
                  <p className="font-medium">{formatDate(event.startsAt, locale)}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {formatTime(event.startsAt, locale)}
                    {event.endsAt && ` ${t.event.until} ${formatTime(event.endsAt, locale)}`}
                  </p>
                </div>
              )}
            </MetaRow>
            <MetaRow icon={<MapPin className="h-4 w-4" />}>
              <p className="font-medium">{event.placeName}</p>
              {/* A link, not an embedded map: nothing is requested from a tile server until someone asks. */}
              <a href={mapUrl} target="_blank" rel="noopener noreferrer" className="mt-0.5 inline-block text-xs font-medium text-primary underline underline-offset-2">
                {t.event.openMap}
              </a>
            </MetaRow>
            <MetaRow icon={<User className="h-4 w-4" />}>
              <p>
                <span className="text-muted-foreground">{t.event.organizer}:&nbsp;</span>
                {event.organizer === null ? (
                  <span className="text-muted-foreground">{t.event.organizerErased}</span>
                ) : event.communitySlug && groupLinked ? (
                  <Link href={routes.community(locale, event.communitySlug)} className="font-medium text-primary underline-offset-2 hover:underline">
                    {event.organizer}
                  </Link>
                ) : (
                  <span className="font-medium">{event.organizer}</span>
                )}
              </p>
            </MetaRow>
            <MetaRow icon={<Footprints className="h-4 w-4" />}>
              <p>
                <span className="text-muted-foreground">{t.event.type}:&nbsp;</span>
                <span className="font-medium">{t.activity[event.activityType]}</span>
              </p>
            </MetaRow>
            {event.difficulty && (
              <MetaRow icon={<Mountain className="h-4 w-4" />}>
                <p>
                  <span className="text-muted-foreground">{t.event.difficulty}:&nbsp;</span>
                  <span className="font-medium">{t.difficulty[event.difficulty]}</span>
                </p>
              </MetaRow>
            )}
            {event.distanceKm !== null && (
              <MetaRow icon={<Navigation className="h-4 w-4" />}>
                <p>
                  <span className="text-muted-foreground">{t.event.distance}:&nbsp;</span>
                  <span className="font-medium">{t.event.kilometres(formatNumber(event.distanceKm, locale))}</span>
                </p>
              </MetaRow>
            )}
            {event.elevationGainM !== null && (
              <MetaRow icon={<TrendingUp className="h-4 w-4" />}>
                <p>
                  <span className="text-muted-foreground">{t.event.elevation}:&nbsp;</span>
                  <span className="font-medium">{t.event.metres(formatNumber(event.elevationGainM, locale, 0))}</span>
                </p>
              </MetaRow>
            )}
          </div>

          <div className="flex flex-col gap-2">
            <h2 className="text-xs font-semibold tracking-widest text-muted-foreground uppercase">{t.event.description}</h2>
            {foreign && <p className="text-xs text-muted-foreground">{t.event.inOrganizerLanguage}</p>}
            <div lang={textLang} className="flex flex-col gap-3 text-sm leading-relaxed">
              {event.description.split(/\n{2,}/).map((paragraph, index) => (
                <p key={index} className="whitespace-pre-line">
                  {paragraph}
                </p>
              ))}
            </div>
          </div>

          <EventLinks heading={t.event.eventLinks} links={event.eventLinks} />
          <EventLinks heading={t.event.communityLinks} links={event.communityLinks} />

          {event.sourceUrl && (
            <div className="flex flex-col gap-2">
              {/* The line SPEC.md §0 requires on every external event: from the source, check it there, and when we last did. */}
              <p className="text-xs text-muted-foreground">
                {t.event.sourceNotice}{" "}
                {event.sourceCheckedAt && t.event.lastChecked(formatDay(event.sourceCheckedAt, locale))}
              </p>
              {/* nofollow: the link is attribution and the way to sign up, not an endorsement. */}
              <Button asChild size="touch" className="w-full">
                <a href={event.sourceUrl} target="_blank" rel="nofollow noopener noreferrer">
                  {t.event.toSource}
                  <ExternalLink className="h-4 w-4" />
                </a>
              </Button>
            </div>
          )}

          {event.status === "PUBLISHED" && !passed && <ReportForm slug={event.slug} t={t.report} />}

          {passed && (
            <Suspense fallback={null}>
              <UpcomingInstead locale={locale} t={t} />
            </Suspense>
          )}
        </article>
      </main>
      <SiteFooter locale={locale} t={t} variant="compact" />
    </>
  );
}

function Chip({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={cn("flex items-center gap-1.5 rounded-full border bg-muted/50 px-2.5 py-1 text-xs font-medium text-foreground", className)}>
      {children}
    </span>
  );
}

function MetaRow({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="flex items-start gap-3 text-sm">
      <span className="mt-0.5 shrink-0 text-muted-foreground">{icon}</span>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

async function UpcomingInstead({ locale, t }: { locale: Locale; t: Dictionary }) {
  const feed = await getFeed(locale, 1);
  if (feed.items.length === 0) {
    return null;
  }
  return (
    <section className="flex flex-col gap-3 border-t pt-6">
      <h2 className="text-lg font-semibold">
        <Link href={routes.feed(locale)} className="hover:underline">
          {t.event.upcomingInstead}
        </Link>
      </h2>
      <ul className="flex flex-col gap-3">
        {feed.items.slice(0, UPCOMING_ON_PAST_PAGE).map((event, index) => (
          <EventListItem key={event.slug} event={event} locale={locale} text={eventCardText(t)} index={index} />
        ))}
      </ul>
    </section>
  );
}

/**
 * schema.org/Event. No offers and no capacity: every stage-one event is
 * external, and imprecise data in markup is worse than none (SPEC.md §0,
 * question 2). A cancellation is declared explicitly (question 3).
 */
function jsonLd(event: EventPage, locale: Locale, t: Dictionary) {
  const url = absoluteUrl(routes.event(locale, event.slug));
  return [
    {
      "@context": "https://schema.org",
      "@type": "Event",
      name: event.name,
      description: event.description,
      startDate: event.startsAt,
      ...(event.endsAt ? { endDate: event.endsAt } : {}),
      eventStatus:
        event.status === "CANCELLED" ? "https://schema.org/EventCancelled" : "https://schema.org/EventScheduled",
      eventAttendanceMode: "https://schema.org/OfflineEventAttendanceMode",
      location: {
        "@type": "Place",
        name: event.placeName,
        address: event.placeName,
        geo: { "@type": "GeoCoordinates", latitude: event.latitude, longitude: event.longitude },
      },
      organizer: {
        "@type": "Organization",
        name: event.organizer,
        ...(event.sourceUrl ? { url: event.sourceUrl } : {}),
      },
      // Whoever leads the outing is the organiser's guide or ranger; the organisation is what the source names.
      performer: { "@type": "Organization", name: event.organizer },
      // Only a price the source states; registration happens at the source.
      ...(event.price
        ? {
            offers: {
              "@type": "Offer",
              price: event.price.amount.toFixed(2),
              priceCurrency: event.price.currency,
              availability: "https://schema.org/InStock",
              ...(event.sourceUrl ? { url: event.sourceUrl } : {}),
            },
            isAccessibleForFree: event.price.amount === 0,
          }
        : {}),
      ...(event.coverImageUrl ? { image: [event.coverImageUrl] } : {}),
      inLanguage: locale,
      url,
    },
    {
      "@context": "https://schema.org",
      "@type": "BreadcrumbList",
      itemListElement: [
        { "@type": "ListItem", position: 1, name: t.nav.home, item: absoluteUrl(routes.home(locale)) },
        { "@type": "ListItem", position: 2, name: t.nav.events, item: absoluteUrl(routes.feed(locale)) },
        { "@type": "ListItem", position: 3, name: event.name, item: url },
      ],
    },
  ];
}
