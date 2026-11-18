import { ArrowRight, Calendar, Footprints, MapPin, Mountain, Navigation } from "lucide-react";
import Link from "next/link";

import { ACTIVITY_ICON, DIFFICULTY_STYLE } from "@/components/activity";
import { Highlighted } from "@/components/highlighted";
import { LogoMark } from "@/components/logo-mark";
import { PriceBadge } from "@/components/price-badge";
import { defaultCover } from "@/lib/covers";
import type { Dictionary } from "@/lib/dictionaries";
import type { EventCard } from "@/lib/events-api";
import { formatMediumDate, formatNumber, formatShortDate, sameDay } from "@/lib/format";
import type { Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { cn } from "@/lib/utils";
import type { ActivityType, Difficulty } from "@/lib/vocabulary";

/**
 * What a card says around the event's own data. Strings only: the list is
 * rendered in the browser too, as it grows while scrolling, and the
 * dictionary's functions cannot cross into it.
 */
export interface EventCardText {
  activity: Record<ActivityType, string>;
  difficulty: Record<Difficulty, string>;
  price: { free: string; paid: string };
  organizer: string;
  /** Carries {km}. */
  kilometres: string;
  stockPhoto: string;
  stockPhotoShort: string;
  /** Which kind of event it is (SPEC.md §18.2): organised here, or found at a source. */
  ownBadge: string;
  sourceBadge: string;
}

export function eventCardText(t: Dictionary): EventCardText {
  return {
    activity: t.activity,
    difficulty: t.difficulty,
    price: t.price,
    organizer: t.event.organizer,
    kilometres: t.event.kilometres("{km}"),
    stockPhoto: t.event.stockPhoto,
    stockPhotoShort: t.event.stockPhotoShort,
    ownBadge: t.event.ownBadge,
    sourceBadge: t.event.sourceBadge,
  };
}

/**
 * One event of the events list, as a row or as a tile (SPEC.md §0.3.2).
 *
 * The row is the working version's: title, tags, a grid of facts. There is no
 * places bar (external events count no places) and the organiser shows for
 * everyone, as plain text from the source. The tile puts the event's photo on
 * top and keeps name, tags with the price, date and place.
 *
 * Both are this one markup, switched by the `tiles:` CSS variant from the
 * attribute on <html> (lib/feed-view.ts), so changing the view re-renders
 * nothing and the server's HTML is right for either. The variant acts only
 * inside the events list (data-feed); elsewhere the card is always a row. The title is the link,
 * stretched over the card, so the card is one target and still one real link.
 */
export function EventListItem({
  event,
  locale,
  text,
  index,
  words = [],
}: {
  event: EventCard;
  locale: Locale;
  text: EventCardText;
  /** Position within its page, for the staggered fade-in. */
  index: number;
  /** Searched words to mark in name, place and organiser (SPEC.md §0.3.1). */
  words?: string[];
}) {
  const ActivityIcon = ACTIVITY_ICON[event.activityType];
  const spansDays = event.endsAt !== null && !sameDay(event.startsAt, event.endsAt);

  return (
    <li
      className="fade-up group relative flex flex-col overflow-hidden rounded-xl border bg-card transition-all hover:border-primary/30 hover:bg-muted/30 focus-within:ring-2 focus-within:ring-ring/50"
      style={{ animationDelay: `${Math.min(index * 40, 300)}ms` }}
    >
      <Cover event={event} text={text} />

      <div className="flex flex-1 flex-col p-4 tiles:p-3.5">
        <h3 className="mb-2.5 text-sm leading-snug font-semibold tiles:line-clamp-2">
          <Link href={routes.event(locale, event.slug)} className="after:absolute after:inset-0 focus-visible:outline-none">
            <Highlighted text={event.name} words={words} />
          </Link>
        </h3>

        <div className="mb-2.5 flex flex-wrap gap-1.5">
          <span className="flex items-center gap-1 rounded-full border bg-muted/50 px-2 py-0.5 text-[10px] font-medium text-muted-foreground">
            <ActivityIcon className="h-2.5 w-2.5" />
            {text.activity[event.activityType]}
          </span>
          {event.difficulty && (
            <span
              className={cn(
                "flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-medium",
                DIFFICULTY_STYLE[event.difficulty].chip,
              )}
            >
              <Mountain className="h-2.5 w-2.5" />
              {text.difficulty[event.difficulty]}
            </span>
          )}
          <PriceBadge amount={event.priceAmount} currency={event.priceCurrency} locale={locale} labels={text.price} />
        </div>

        <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 tiles:grid-cols-1">
          <Fact icon={Calendar}>
            {formatMediumDate(event.startsAt, locale)}
            {spansDays && event.endsAt && (
              <span className="text-muted-foreground/70">
                {" "}
                <ArrowRight className="inline h-3 w-3" /> {formatShortDate(event.endsAt, locale)}
              </span>
            )}
          </Fact>
          {/* Two lines, then an ellipsis: a meeting point with its street is often longer than half a row. */}
          <Fact icon={MapPin} lines={2} title={event.placeName}>
            <Highlighted text={event.placeName} words={words} />
          </Fact>
          {event.distanceKm !== null && (
            <Fact icon={Navigation} className="tiles:hidden">
              {text.kilometres.replace("{km}", formatNumber(event.distanceKm, locale))}
            </Fact>
          )}
          <Fact icon={Footprints} wide className="tiles:hidden" title={event.organizer}>
            {text.organizer}:{" "}
            <span className="text-foreground/70">
              <Highlighted text={event.organizer} words={words} />
            </span>
            {" · "}
            <span className={event.kind === "OWN" ? "font-medium text-primary" : undefined}>
              {event.kind === "OWN" ? text.ownBadge : text.sourceBadge}
            </span>
          </Fact>
        </div>
      </div>
    </li>
  );
}

/**
 * The tile's photo, hidden in the list. Lazy, so a list visitor's browser never
 * fetches it: an image that is not displayed never comes near the viewport.
 * Without a photo of its own an event shows its activity's stand-in, marked as
 * such, as on the event page.
 */
function Cover({ event, text }: { event: EventCard; text: EventCardText }) {
  const standIn = event.coverImageUrl ? null : defaultCover(event.activityType);
  const image = "h-full w-full object-cover transition-transform duration-500 group-hover:scale-105";

  return (
    <div className="relative hidden aspect-video overflow-hidden bg-linear-to-br from-primary/20 via-primary/5 to-transparent tiles:block">
      {event.coverImageUrl ? (
        // A freely licensed photo in our own storage; its credit is the title and is in full on the event page.
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={event.coverImageUrl}
          alt=""
          title={event.coverCredit ?? undefined}
          loading="lazy"
          decoding="async"
          className={image}
        />
      ) : standIn ? (
        <>
          {/* Already sized and compressed in the bucket; next/image would only re-encode it on this server. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={standIn.small}
            srcSet={standIn.srcSet}
            sizes="(min-width: 640px) 24rem, 100vw"
            width={standIn.width}
            height={standIn.height}
            alt=""
            loading="lazy"
            decoding="async"
            className={image}
          />
          <span
            title={text.stockPhoto}
            className="absolute bottom-2 left-2 rounded-md bg-background/85 px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground backdrop-blur"
          >
            {text.stockPhotoShort}
          </span>
        </>
      ) : (
        <LogoMark className="absolute -right-2 -bottom-4 h-32 w-32 bg-primary/15" />
      )}
    </div>
  );
}

/**
 * A fact with its icon. The text is cut where the column ends, on one line or
 * after two; the cut sits on the flex item itself, because an inline element
 * ignores overflow and the text then ran past the card's edge.
 */
function Fact({
  icon: Icon,
  children,
  wide = false,
  lines = 1,
  title,
  className,
}: {
  icon: typeof Calendar;
  children: React.ReactNode;
  wide?: boolean;
  lines?: 1 | 2;
  /** The whole text, shown on hover where it was cut. */
  title?: string;
  className?: string;
}) {
  return (
    <div className={cn("flex min-w-0 items-start gap-1.5 text-xs text-muted-foreground", wide && "col-span-2", className)}>
      <Icon className="mt-px h-3.5 w-3.5 shrink-0 text-muted-foreground/60" />
      <span title={title} className={cn("min-w-0", lines === 1 ? "truncate" : "line-clamp-2 wrap-break-word")}>
        {children}
      </span>
    </div>
  );
}
