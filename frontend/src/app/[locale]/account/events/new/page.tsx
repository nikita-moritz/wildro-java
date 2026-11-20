import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { EventEditor } from "@/components/own/event-editor";
import { Notice } from "@/components/own/notice";
import { manages, myCommunities } from "@/lib/communities";
import { getDictionary } from "@/lib/dictionaries";
import { mapboxToken } from "@/lib/env";
import { isLocale } from "@/lib/locales";
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

export default async function NewEventPage({ params }: PageProps<"/[locale]/account/events/new">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const session = await requireSignedIn(locale);
  // Said up front rather than after the form is filled in.
  const blocker = !session
    ? t.auth.unavailable
    : !session.me.terms.upToDate
      ? t.own.errors.TERMS_REQUIRED
      : !session.me.displayName
        ? t.own.errors.DISPLAY_NAME_REQUIRED
        : null;
  const writable = session
    ? ((await myCommunities(session.jwt)) ?? []).filter((each) => manages(each.role) && each.status === "ACTIVE")
    : [];

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-4 py-10">
      <BackLink href={routes.myEvents(locale)} label={t.own.back} />
      <h1 className="text-2xl font-semibold tracking-tight">{t.own.editorNew}</h1>
      {blocker ? (
        <>
          <Notice text={blocker} tone="alert" />
          <Link href={routes.account(locale)} className="text-sm text-primary underline-offset-2 hover:underline">
            {t.nav.account}
          </Link>
        </>
      ) : (
        <EventEditor
          locale={locale}
          initial={null}
          t={{ own: t.own, activity: t.activity, difficulty: t.difficulty }}
          mapboxToken={mapboxToken() ?? null}
          editPath={routes.editEvent(locale, "{slug}")}
          communities={writable.map(({ slug, name }) => ({ slug, name }))}
        />
      )}
    </main>
  );
}
