import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { Notice } from "@/components/own/notice";
import { Button } from "@/components/ui/button";
import { getDictionary } from "@/lib/dictionaries";
import { formatDate } from "@/lib/format";
import { isLocale } from "@/lib/locales";
import { listMine } from "@/lib/own-events";
import { routes } from "@/lib/routes";
import { requireSignedIn } from "@/lib/signed-in-page";

export async function generateMetadata({ params }: PageProps<"/[locale]/account/events">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return { title: getDictionary(locale).own.myEventsTitle, robots: { index: false, follow: false } };
}

export const instant = false;

export default async function MyEventsPage({ params }: PageProps<"/[locale]/account/events">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const session = await requireSignedIn(locale);
  const events = session ? await listMine(session.jwt) : null;

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-4 py-10">
      <Link href={routes.account(locale)} className="text-sm text-muted-foreground hover:text-foreground">
        {t.own.back}
      </Link>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{t.own.myEventsTitle}</h1>
        <Button asChild className="h-11">
          <Link href={routes.newEvent(locale)}>{t.own.newEvent}</Link>
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">{t.own.myEventsLead}</p>

      {events === null ? (
        <Notice text={t.auth.unavailable} tone="alert" />
      ) : events.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t.own.empty}</p>
      ) : (
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
      )}
    </main>
  );
}
