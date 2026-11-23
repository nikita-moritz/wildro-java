import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { ConfirmForm } from "@/components/confirm-form";
import { EventEditor } from "@/components/own/event-editor";
import { Notice } from "@/components/own/notice";
import { Button } from "@/components/ui/button";
import { defaultCover } from "@/lib/covers";
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
    query.done === "saved"
      ? t.own.saved
      : query.done === "publish"
        ? t.own.published
        : query.done === "cancel"
          ? t.own.cancelledDone
          : query.done === "cover"
            ? t.own.coverDone
            : null;
  const stand = defaultCover(event.activityType);
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
      {/* Just published: the next step is telling people. The public page opens its share dialog on #share. */}
      {query.done === "publish" && event.status === "PUBLISHED" && (
        <Button asChild className="h-11 self-start">
          <Link href={`${routes.event(locale, event.slug)}#share`}>{t.own.shareNow}</Link>
        </Button>
      )}
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
        {/* Any of one's events can start the next one, a past or cancelled one too; a suspended one cannot. */}
        {event.status !== "SUSPENDED" && (
          <Button asChild variant="outline" className="h-11">
            <Link href={routes.newEvent(locale, { from: event.slug })}>{t.own.duplicate}</Link>
          </Button>
        )}
        {editable && (
          <ConfirmForm
            action={`/api/own/events/${encodeURIComponent(event.slug)}/cancel`}
            fields={{ locale }}
            label={t.own.cancelEvent}
            question={t.own.cancelConfirm}
            yes={t.own.cancelYes}
          />
        )}
      </div>

      {editable && (
        <section className="flex flex-col gap-3">
          <h2 className="font-semibold">{t.own.coverTitle}</h2>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
            {/* Your own picture, or the activity's stand-in that shows meanwhile. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={event.coverImageUrl ?? stand?.small ?? ""}
              alt=""
              className="aspect-[16/9] w-full rounded-xl border bg-muted object-cover sm:w-64"
            />
            <p className="text-sm text-muted-foreground">{event.coverImageUrl ? t.own.coverHint : t.own.coverStandIn}</p>
          </div>
          {/* A plain multipart form: the server cuts the picture, nothing runs in the browser. */}
          <form
            method="post"
            action={`/api/own/events/${encodeURIComponent(event.slug)}/cover`}
            encType="multipart/form-data"
            className="flex flex-wrap items-center gap-2"
          >
            <input type="hidden" name="locale" value={locale} />
            <input type="hidden" name="intent" value="upload" />
            <label className="flex min-h-11 w-full cursor-pointer items-center rounded-lg border bg-background px-3 text-sm sm:w-auto sm:flex-1">
              <span className="sr-only">{t.own.coverChoose}</span>
              <input
                type="file"
                name="cover"
                required
                accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
                className="w-full text-sm file:mr-3 file:rounded-full file:border-0 file:bg-muted file:px-3 file:py-1.5 file:text-sm file:font-medium"
              />
            </label>
            <Button type="submit" className="h-11">
              {t.own.coverUpload}
            </Button>
          </form>
          {event.coverImageUrl && (
            <form method="post" action={`/api/own/events/${encodeURIComponent(event.slug)}/cover`}>
              <input type="hidden" name="locale" value={locale} />
              <input type="hidden" name="intent" value="remove" />
              <Button type="submit" variant="outline" size="sm" className="text-destructive">
                {t.own.coverRemove}
              </Button>
            </form>
          )}
        </section>
      )}

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
