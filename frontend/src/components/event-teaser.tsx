import { MapPin, Navigation, TrendingUp } from "lucide-react";
import Link from "next/link";

import { ACTIVITY_ICON, DIFFICULTY_STYLE } from "@/components/activity";
import { LogoMark } from "@/components/logo-mark";
import type { Dictionary } from "@/lib/dictionaries";
import type { EventCard } from "@/lib/events-api";
import { defaultCover } from "@/lib/covers";
import { formatNumber, formatShortDate, formatTime } from "@/lib/format";
import type { Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";

/**
 * The home page card of the working version. Where it showed a places bar
 * ("3 of 12, 9 left") this shows the activity: every stage-one event is
 * external, nobody counts places for it, and showing a count would promise one
 * (SPEC.md §0, question 2).
 */
export function EventTeaser({ event, locale, t }: { event: EventCard; locale: Locale; t: Dictionary }) {
  const ActivityIcon = ACTIVITY_ICON[event.activityType];
  const standIn = defaultCover(event.activityType);

  return (
    <Link
      href={routes.event(locale, event.slug)}
      className="group relative flex flex-col overflow-hidden rounded-2xl border bg-card transition-all hover:border-primary/40 hover:shadow-lg focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none"
    >
      <div className="relative h-40 overflow-hidden bg-linear-to-br from-primary/20 via-primary/5 to-transparent">
        {event.coverImageUrl ? (
          // A freely licensed photo in our own storage; its credit is the title and is in full on the event page.
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={event.coverImageUrl}
            alt=""
            title={event.coverCredit ?? undefined}
            loading="lazy"
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        ) : standIn ? (
          // Already sized and compressed in the bucket; next/image would only re-encode it on this server.
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={standIn.small}
            srcSet={standIn.srcSet}
            sizes="(min-width: 1024px) 24rem, (min-width: 640px) 50vw, 100vw"
            width={standIn.width}
            height={standIn.height}
            alt=""
            loading="lazy"
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        ) : (
          <LogoMark className="absolute -right-2 -bottom-4 h-32 w-32 bg-primary/15" />
        )}
        <span className="absolute top-3 left-3 rounded-lg bg-background/90 px-2.5 py-1 text-xs font-semibold backdrop-blur">
          {formatShortDate(event.startsAt, locale)} · {formatTime(event.startsAt, locale)}
        </span>
        {event.difficulty && (
          <span className="absolute top-3 right-3 flex items-center gap-1.5 rounded-lg bg-background/90 px-2.5 py-1 text-xs font-medium backdrop-blur">
            <span className={`h-2 w-2 rounded-full ${DIFFICULTY_STYLE[event.difficulty].dot}`} />
            {t.difficulty[event.difficulty]}
          </span>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-3 p-4">
        <h3 className="line-clamp-2 leading-snug font-semibold group-hover:text-primary">{event.name}</h3>
        <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <MapPin className="h-3.5 w-3.5 shrink-0" />
            {event.placeName}
          </span>
          {event.distanceKm !== null && (
            <span className="flex items-center gap-1.5">
              <Navigation className="h-3.5 w-3.5 shrink-0" />
              {t.event.kilometres(formatNumber(event.distanceKm, locale))}
            </span>
          )}
          {event.elevationGainM !== null && event.elevationGainM > 0 && (
            <span className="flex items-center gap-1.5">
              <TrendingUp className="h-3.5 w-3.5 shrink-0" />
              {t.event.metres(formatNumber(event.elevationGainM, locale, 0))}
            </span>
          )}
        </div>
        <div className="mt-auto flex items-center gap-1.5 text-xs font-medium text-primary">
          <ActivityIcon className="h-3.5 w-3.5 shrink-0" />
          {t.activity[event.activityType]}
        </div>
      </div>
    </Link>
  );
}
