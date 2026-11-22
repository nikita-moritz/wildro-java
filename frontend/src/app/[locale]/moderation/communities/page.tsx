import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { CommunityLogo } from "@/components/community/community-logo";
import { ModerationTabs } from "@/components/moderation/moderation-tabs";
import { Notice } from "@/components/own/notice";
import { Button } from "@/components/ui/button";
import { moderatedCommunities } from "@/lib/communities";
import { communityCode } from "@/lib/community-answer";
import { getDictionary } from "@/lib/dictionaries";
import { formatDay } from "@/lib/format";
import { isLocale } from "@/lib/locales";
import { moderates } from "@/lib/own-events";
import { routes } from "@/lib/routes";
import { requireSignedIn } from "@/lib/signed-in-page";

export async function generateMetadata({ params }: PageProps<"/[locale]/moderation/communities">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale).moderation;
  return { title: `${t.tabCommunities} · ${t.title}`, robots: { index: false, follow: false } };
}

export const instant = false;

/**
 * Groups as moderation sees them (SPEC.md §18.1, §18.4): take one off the site
 * with a reason, put it back, and for a superuser hand its ownership to one of
 * its admins. The community service checks every role again; what is hidden
 * here is convenience, not protection.
 */
export default async function ModerateCommunitiesPage({ params, searchParams }: PageProps<"/[locale]/moderation/communities">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const m = t.moderation;
  const query = await searchParams;
  const q = typeof query.q === "string" ? query.q.slice(0, 100) : "";
  const session = await requireSignedIn(locale);
  if (session && !moderates(session.me.roles)) {
    notFound();
  }
  const superuser = session?.me.roles.includes("superuser") ?? false;
  const communities = session ? await moderatedCommunities(session.jwt, q) : null;
  const failed = typeof query.error === "string" ? t.community.errors[communityCode(query.error)] : null;
  const hidden = (slug: string) => (
    <>
      <input type="hidden" name="locale" value={locale} />
      <input type="hidden" name="slug" value={slug} />
      <input type="hidden" name="q" value={q} />
    </>
  );

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-10">
      <BackLink href={routes.account(locale)} label={t.nav.back} className="-mb-4" />
      <h1 className="text-2xl font-semibold tracking-tight">{m.title}</h1>
      <ModerationTabs locale={locale} t={t} current="communities" />
      <p className="text-sm text-muted-foreground">{m.communitiesLead}</p>
      <Notice text={query.done === "1" ? m.done : null} />
      <Notice text={failed} tone="alert" />

      <form method="get" role="search" className="flex flex-col gap-2 sm:flex-row">
        <label className="sr-only" htmlFor="moderation-search">
          {m.search}
        </label>
        <input
          id="moderation-search"
          name="q"
          type="search"
          defaultValue={q}
          maxLength={100}
          placeholder={m.search}
          className="h-11 w-full rounded-lg border bg-background px-3 text-base"
        />
        <Button type="submit" variant="outline" className="h-11">
          {m.searchButton}
        </Button>
      </form>

      {communities === null ? (
        <Notice text={t.auth.unavailable} tone="alert" />
      ) : communities.length === 0 ? (
        <p className="text-sm text-muted-foreground">{m.communitiesEmpty}</p>
      ) : (
        <ul className="flex flex-col gap-4">
          {communities.map((community) => {
            const suspended = community.status === "SUSPENDED";
            const owner = community.leaders.find((leader) => leader.role === "OWNER");
            const admins = community.leaders.filter((leader) => leader.role === "ADMIN");
            return (
              <li key={community.slug} className="flex flex-col gap-3 rounded-xl border bg-card p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="flex min-w-0 items-center gap-3">
                    <CommunityLogo url={community.logoUrl} name={community.name} size={40} />
                    {suspended ? (
                      <span className="font-medium">{community.name}</span>
                    ) : (
                      <Link href={routes.community(locale, community.slug)} className="font-medium underline-offset-2 hover:underline">
                        {community.name}
                      </Link>
                    )}
                  </span>
                  {suspended ? (
                    <span className="rounded-full border border-destructive/30 bg-destructive/10 px-2.5 py-0.5 text-xs font-medium text-destructive">
                      {m.suspendedBadge}
                    </span>
                  ) : null}
                </div>
                <p className="text-xs text-muted-foreground">
                  {community.area} · {t.community.members.replace("{n}", String(community.memberCount))} ·{" "}
                  {formatDay(community.createdAt, locale)} · {community.slug}
                </p>
                {owner ? (
                  <p className="text-sm">
                    <span className="text-muted-foreground">{t.community.roleOWNER}: </span>
                    {owner.displayName}
                  </p>
                ) : null}

                {community.logoUrl && (
                  // The picture alone: the group stays, the owner is told the reason.
                  <form method="post" action="/api/moderation/communities/logo-remove" className="flex flex-col gap-2 rounded-lg border p-3">
                    {hidden(community.slug)}
                    <div className="flex items-center gap-3">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src={community.logoUrl} alt="" width={96} height={96} className="h-24 w-24 rounded-lg border object-cover" />
                      <label className="flex flex-1 flex-col gap-1.5 text-sm font-medium">
                        {m.logoReason}
                        <textarea name="reason" required maxLength={2000} rows={2} className="w-full rounded-lg border bg-background px-3 py-2 text-base" />
                      </label>
                    </div>
                    <Button type="submit" variant="outline" className="h-11 self-start">
                      {m.logoRemove}
                    </Button>
                  </form>
                )}

                {community.reports.length > 0 && (
                  // The open reports, oldest first. Removing the group below settles them all.
                  <ul className="flex flex-col gap-2">
                    {community.reports.map((report) => (
                      <li key={report.id} className="flex flex-col gap-2 rounded-lg border bg-muted/40 p-3 text-sm">
                        <p>
                          <span className="text-muted-foreground">{m.reason}: </span>
                          {t.report.reasons[report.reason]}
                          <span className="text-xs text-muted-foreground">
                            {" "}
                            · {m.reportedAt} {formatDay(report.createdAt, locale)}
                          </span>
                        </p>
                        <p className="whitespace-pre-line">{report.details}</p>
                        <form method="post" action="/api/moderation/communities/dismiss">
                          {hidden(community.slug)}
                          <input type="hidden" name="report" value={String(report.id)} />
                          <Button type="submit" variant="outline" className="h-11">
                            {m.dismiss}
                          </Button>
                        </form>
                      </li>
                    ))}
                  </ul>
                )}

                {suspended ? (
                  <>
                    <p className="text-sm whitespace-pre-line">
                      <span className="text-muted-foreground">{m.reason}: </span>
                      {community.suspensionReason}
                    </p>
                    <form method="post" action="/api/moderation/communities/reinstate">
                      {hidden(community.slug)}
                      <Button type="submit" variant="outline" className="h-11">
                        {m.reinstate}
                      </Button>
                    </form>
                  </>
                ) : (
                  <form method="post" action="/api/moderation/communities/suspend" className="flex flex-col gap-2">
                    {hidden(community.slug)}
                    <label className="flex flex-col gap-1.5 text-sm font-medium">
                      {m.communityReason}
                      <textarea name="reason" required maxLength={2000} rows={2} className="w-full rounded-lg border bg-background px-3 py-2 text-base" />
                    </label>
                    <Button type="submit" className="h-11 self-start">
                      {m.suspend}
                    </Button>
                  </form>
                )}

                {superuser ? (
                  admins.length === 0 ? (
                    <p className="text-xs text-muted-foreground">{m.noAdmins}</p>
                  ) : (
                    <form method="post" action="/api/moderation/communities/transfer" className="flex flex-col gap-2 border-t pt-3">
                      {hidden(community.slug)}
                      <label className="flex flex-col gap-1.5 text-sm font-medium">
                        {m.transferTo}
                        <select name="user" required className="h-11 w-full rounded-lg border bg-background px-3 text-base">
                          {admins.map((admin) => (
                            <option key={admin.userId} value={admin.userId}>
                              {admin.displayName}
                            </option>
                          ))}
                        </select>
                      </label>
                      <p className="text-xs text-muted-foreground">{m.transferHint}</p>
                      <Button type="submit" variant="outline" className="h-11 self-start">
                        {m.transfer}
                      </Button>
                    </form>
                  )
                ) : null}
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
