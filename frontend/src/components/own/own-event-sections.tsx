import Link from "next/link";

import type { Dictionary } from "@/lib/dictionaries";
import { formatDate } from "@/lib/format";
import type { Locale } from "@/lib/locales";
import type { OwnEvent } from "@/lib/own-events";
import { routes } from "@/lib/routes";

/**
 * Events one may edit, in the order work happens: drafts still to publish,
 * what is coming up (soonest first), and what is over (latest first, folded
 * away, kept at hand for copying). Each row opens the editor.
 */
export function OwnEventSections({
  events,
  now,
  locale,
  t,
  empty,
}: {
  events: OwnEvent[];
  now: number;
  locale: Locale;
  t: Dictionary;
  empty: string;
}) {
  const isOver = (event: OwnEvent) => Date.parse(event.endsAt ?? event.startsAt) < now;
  const soonest = (a: OwnEvent, b: OwnEvent) => Date.parse(a.startsAt) - Date.parse(b.startsAt);
  const drafts = events.filter((event) => event.status === "DRAFT" && !isOver(event)).sort(soonest);
  const ahead = events.filter((event) => event.status !== "DRAFT" && !isOver(event)).sort(soonest);
  const past = events.filter(isOver).sort((a, b) => soonest(b, a));

  if (drafts.length === 0 && ahead.length === 0 && past.length === 0) {
    return <p className="text-sm text-muted-foreground">{empty}</p>;
  }
  return (
    <div className="flex flex-col gap-4">
      {drafts.length > 0 && <Part title={t.own.sectionDrafts} events={drafts} locale={locale} t={t} />}
      {ahead.length > 0 && <Part title={t.own.sectionUpcoming} events={ahead} locale={locale} t={t} />}
      {ahead.length === 0 && drafts.length === 0 && <p className="text-sm text-muted-foreground">{empty}</p>}
      {past.length > 0 && (
        <details className="group">
          <summary className="flex min-h-11 cursor-pointer list-none items-center gap-2 text-sm font-medium text-muted-foreground [&::-webkit-details-marker]:hidden">
            {t.own.sectionPast} ({past.length})
          </summary>
          <Rows events={past} locale={locale} t={t} />
        </details>
      )}
    </div>
  );
}

function Part({ title, events, locale, t }: { title: string; events: OwnEvent[]; locale: Locale; t: Dictionary }) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-sm font-medium text-muted-foreground">
        {title} ({events.length})
      </h3>
      <Rows events={events} locale={locale} t={t} />
    </div>
  );
}

function Rows({ events, locale, t }: { events: OwnEvent[]; locale: Locale; t: Dictionary }) {
  return (
    <ul className="flex flex-col gap-3">
      {events.map((event) => (
        <li key={event.slug}>
          <Link
            href={routes.editEvent(locale, event.slug)}
            className="flex flex-col gap-1 rounded-xl border bg-card p-4 transition-colors hover:bg-muted"
          >
            <span className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium">{event.name}</span>
              <span className="rounded-full border px-2.5 py-0.5 text-xs">{t.own[`status${event.status}`]}</span>
            </span>
            <span className="text-sm text-muted-foreground">
              {formatDate(event.startsAt, locale)} · {event.placeName}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
