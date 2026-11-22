import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { EventEditor } from "@/components/own/event-editor";
import { Notice } from "@/components/own/notice";
import { Button } from "@/components/ui/button";
import { getDictionary } from "@/lib/dictionaries";
import { mapboxToken } from "@/lib/env";
import { isLocale } from "@/lib/locales";
import { errorCode, getMine } from "@/lib/own-events";
import { routes } from "@/lib/routes";
import { requireSignedIn } from "@/lib/signed-in-page";

export async function generateMetadata({ params }: PageProps<"/[locale]/account/events/[slug]">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return { title: getDictionary(locale).own.editorEdit, robots: { index: false, follow: false } };
}

export const instant = false;

export default async function EditEventPage({ params, searchParams }: PageProps<"/[locale]/account/events/[slug]">) {
  const { locale, slug } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const query = await searchParams;
  const session = await requireSignedIn(locale);
  if (!session) {
    return (
      <main className="mx-auto w-full max-w-2xl flex-1 px-4 py-10">
        <Notice text={t.auth.unavailable} tone="alert" />
      </main>
    );
  }
  const event = await getMine(session.jwt, slug);
  if (!event) {
    notFound();
  }
  const done =
    query.done === "saved" ? t.own.saved : query.done === "publish" ? t.own.published : query.done === "cancel" ? t.own.cancelledDone : null;
  const failed = typeof query.error === "string" ? t.own.errors[errorCode(query.error)] : null;
  const editable = event.status === "DRAFT" || event.status === "PUBLISHED";

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-4 py-10">
      <BackLink href={routes.myEvents(locale)} label={t.own.back} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{t.own.editorEdit}</h1>
        <span className="rounded-full border px-3 py-1 text-sm">{t.own[`status${event.status}`]}</span>
      </div>

      <p className="text-sm text-muted-foreground">
        {t.own.ownerLabel}: {event.organizer}
      </p>
      <Notice text={done} />
      <Notice text={failed} tone="alert" />
      {event.status === "SUSPENDED" && event.suspensionReason && (
        <Notice text={`${t.own.suspendedBecause} ${event.suspensionReason}`} tone="alert" />
      )}

      <div className="flex flex-wrap gap-2">
        {(event.status === "PUBLISHED" || event.status === "CANCELLED") && (
          <Button asChild variant="outline" className="h-11">
            <Link href={routes.event(locale, event.slug)}>{t.own.viewPublic}</Link>
          </Button>
        )}
        {event.status === "DRAFT" && (
          <form method="post" action={`/api/own/events/${encodeURIComponent(event.slug)}/publish`}>
            <input type="hidden" name="locale" value={locale} />
            <Button type="submit" className="h-11">
              {t.own.publish}
            </Button>
          </form>
        )}
        {editable && (
          <form method="post" action={`/api/own/events/${encodeURIComponent(event.slug)}/cancel`}>
            <input type="hidden" name="locale" value={locale} />
            <Button type="submit" variant="outline" className="h-11 text-destructive">
              {t.own.cancelEvent}
            </Button>
          </form>
        )}
      </div>

      {editable && (
        <EventEditor
          locale={locale}
          initial={event}
          t={{ own: t.own, activity: t.activity, difficulty: t.difficulty, filters: t.filters }}
          mapboxToken={mapboxToken() ?? null}
          editPath={routes.editEvent(locale, "{slug}")}
        />
      )}
    </main>
  );
}
