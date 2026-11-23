import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { EventEditor } from "@/components/own/event-editor";
import { GetReady } from "@/components/own/get-ready";
import { Notice } from "@/components/own/notice";
import { manages, myCommunities } from "@/lib/communities";
import { getDictionary } from "@/lib/dictionaries";
import { mapboxToken } from "@/lib/env";
import { isLocale } from "@/lib/locales";
import { getMine } from "@/lib/own-events";
import { routes } from "@/lib/routes";
import { requireSignedIn } from "@/lib/signed-in-page";

export async function generateMetadata({ params }: PageProps<"/[locale]/account/events/new">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return { title: getDictionary(locale).own.editorNew, robots: { index: false, follow: false } };
}

export const instant = false;

export default async function NewEventPage({ params, searchParams }: PageProps<"/[locale]/account/events/new">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const query = await searchParams;
  const from = typeof query.from === "string" ? query.from : undefined;
  const group = typeof query.group === "string" ? query.group : undefined;
  // The way back after signing in is a bare path (lib/signed-in-page.ts): a duplicate starts from its original's page anyway.
  const session = await requireSignedIn(locale, routes.newEvent(locale));
  // "Duplicate": the original as the event service shows it to this person, so only their own.
  const original = session && from ? await getMine(session.jwt, from) : null;
  // Asked up front, on this page, rather than after the form is filled in.
  const needsName = !!session && !session.me.displayName;
  const needsTerms = !!session && !session.me.terms.upToDate;
  const writable = session
    ? ((await myCommunities(session.jwt)) ?? []).filter((each) => manages(each.role) && each.status === "ACTIVE")
    : [];

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-4 py-10">
      <BackLink href={routes.myEvents(locale)} label={t.own.back} />
      <h1 className="text-2xl font-semibold tracking-tight">{original ? t.own.duplicateTitle : t.own.editorNew}</h1>
      {original && session && <p className="text-sm text-muted-foreground">{t.own.duplicateLead}</p>}
      {!session ? (
        <Notice text={t.auth.unavailable} tone="alert" />
      ) : needsName || needsTerms ? (
        <GetReady
          locale={locale}
          t={t}
          next={routes.newEvent(locale, { from, group })}
          needsName={needsName}
          needsTerms={needsTerms}
        />
      ) : (
        <EventEditor
          locale={locale}
          initial={null}
          t={{ own: t.own, activity: t.activity, difficulty: t.difficulty, filters: t.filters }}
          mapboxToken={mapboxToken() ?? null}
          editPath={routes.editEvent(locale, "{slug}")}
          communities={writable.map(({ slug, name }) => ({ slug, name }))}
          forCommunity={group ?? null}
          copyOf={
            original
              ? { from: original, communitySlug: original.communitySlug, coverImageUrl: original.coverImageUrl }
              : null
          }
        />
      )}
    </main>
  );
}
