import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { ModerationTabs } from "@/components/moderation/moderation-tabs";
import { Notice } from "@/components/own/notice";
import { Button } from "@/components/ui/button";
import { getDictionary } from "@/lib/dictionaries";
import { formatDay } from "@/lib/format";
import { isLocale } from "@/lib/locales";
import { moderates, openReports } from "@/lib/own-events";
import { routes } from "@/lib/routes";
import { requireSignedIn } from "@/lib/signed-in-page";

export async function generateMetadata({ params }: PageProps<"/[locale]/moderation">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return { title: getDictionary(locale).moderation.title, robots: { index: false, follow: false } };
}

export const instant = false;

/**
 * The report queue (SPEC.md §18.2). Shown to moderators and superusers; the
 * event service checks the role again on every action, so hiding the page from
 * others is convenience, not protection.
 */
export default async function ModerationPage({ params, searchParams }: PageProps<"/[locale]/moderation">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const query = await searchParams;
  const session = await requireSignedIn(locale);
  if (session && !moderates(session.me.roles)) {
    notFound();
  }
  const reports = session ? await openReports(session.jwt) : null;

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-10">
      <BackLink href={routes.account(locale)} label={t.nav.back} className="-mb-4" />
      <h1 className="text-2xl font-semibold tracking-tight">{t.moderation.title}</h1>
      <ModerationTabs locale={locale} t={t} current="reports" />
      <p className="text-sm text-muted-foreground">{t.moderation.lead}</p>
      <Notice text={query.done === "1" ? t.moderation.done : null} />
      <Notice text={typeof query.error === "string" ? t.own.errors.WRONG_STATE : null} tone="alert" />

      {reports === null ? (
        <Notice text={t.auth.unavailable} tone="alert" />
      ) : reports.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t.moderation.empty}</p>
      ) : (
        <ul className="flex flex-col gap-4">
          {reports.map((report) => (
            <li key={report.id} className="flex flex-col gap-3 rounded-xl border bg-card p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <Link href={routes.event(locale, report.slug)} className="font-medium underline-offset-2 hover:underline">
                  {report.eventName}
                </Link>
                <span className="text-xs text-muted-foreground">
                  {t.moderation.reportedAt} {formatDay(report.createdAt, locale)}
                </span>
              </div>
              <p className="text-sm">
                <span className="text-muted-foreground">{t.moderation.reason}: </span>
                {t.report.reasons[report.reason]}
              </p>
              <p className="text-sm whitespace-pre-line">{report.details}</p>
              <p className="text-xs text-muted-foreground">
                {t.event.organizer}: {report.organizer} · {report.kind === "OWN" ? t.event.ownBadge : t.event.sourceBadge}
              </p>
              <form method="post" action="/api/moderation/suspend" className="flex flex-col gap-2">
                <input type="hidden" name="locale" value={locale} />
                <input type="hidden" name="slug" value={report.slug} />
                <label className="flex flex-col gap-1.5 text-sm font-medium">
                  {t.moderation.suspendReason}
                  <textarea name="reason" required maxLength={2000} rows={2} className="w-full rounded-lg border bg-background px-3 py-2 text-base" />
                </label>
                <div className="flex flex-wrap gap-2">
                  <Button type="submit" className="h-11">
                    {t.moderation.suspend}
                  </Button>
                  <Button type="submit" variant="outline" className="h-11" formAction="/api/moderation/dismiss" formNoValidate name="report" value={String(report.id)}>
                    {t.moderation.dismiss}
                  </Button>
                </div>
              </form>
            </li>
          ))}
        </ul>
      )}

      {/* A removal is undone by the event's address, which the decision notice names. */}
      <form method="post" action="/api/moderation/reinstate" className="flex flex-col gap-2 border-t pt-6">
        <input type="hidden" name="locale" value={locale} />
        <label className="flex flex-col gap-1.5 text-sm font-medium">
          {t.moderation.reinstate}
          <input name="slug" required placeholder="event-address-2026-11-25" className="h-11 w-full rounded-lg border bg-background px-3 text-base" />
        </label>
        <Button type="submit" variant="outline" className="h-11 self-start">
          {t.moderation.reinstate}
        </Button>
      </form>
    </main>
  );
}
