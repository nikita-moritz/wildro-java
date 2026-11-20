import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { CommunityEditor } from "@/components/community/community-editor";
import { Notice } from "@/components/own/notice";
import { Button } from "@/components/ui/button";
import { getCommunity, getCommunityEvents, manages, membersOf, standingIn } from "@/lib/communities";
import { communityCode } from "@/lib/community-answer";
import { getDictionary } from "@/lib/dictionaries";
import { formatDay } from "@/lib/format";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { requireSignedIn } from "@/lib/signed-in-page";

export async function generateMetadata({ params }: PageProps<"/[locale]/account/communities/[slug]">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return { title: getDictionary(locale).community.manage, robots: { index: false, follow: false } };
}

export const instant = false;

/**
 * A member's view of their community: the member list for everyone in it; the
 * editor and member controls for admins; handing over and closing for the
 * owner. Each button is a plain form; the community service decides.
 */
export default async function ManageCommunityPage({ params, searchParams }: PageProps<"/[locale]/account/communities/[slug]">) {
  const { locale, slug } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const c = t.community;
  const query = await searchParams;
  const session = await requireSignedIn(locale);
  if (!session) {
    return (
      <main className="mx-auto w-full max-w-2xl flex-1 px-4 py-10">
        <Notice text={t.auth.unavailable} tone="alert" />
      </main>
    );
  }
  const [community, standing, members] = await Promise.all([
    getCommunity(slug),
    standingIn(session.jwt, slug),
    membersOf(session.jwt, slug),
  ]);
  if (!community || !standing?.role || !members) {
    notFound();
  }
  const role = standing.role;
  const active = community.status === "ACTIVE";
  const upcoming = role === "OWNER" && active ? (await getCommunityEvents(community.id, locale)).length : 0;
  const failed = typeof query.error === "string" ? c.errors[communityCode(query.error)] : null;
  const form = (action: string, fields: Record<string, string>, label: string, variant: "default" | "outline" = "outline", danger = false) => (
    <form method="post" action={`/api/communities/${encodeURIComponent(slug)}/${action}`}>
      <input type="hidden" name="locale" value={locale} />
      {Object.entries(fields).map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      <Button type="submit" variant={variant} size="sm" className={danger ? "text-destructive" : undefined}>
        {label}
      </Button>
    </form>
  );

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-8 px-4 py-10">
      <BackLink href={routes.myCommunities(locale)} label={c.back} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{community.name}</h1>
        <span className="rounded-full border px-3 py-1 text-sm">{c[`role${role}`]}</span>
      </div>
      <Notice text={query.done === "1" ? c.done : null} />
      <Notice text={failed} tone="alert" />
      {!active && <Notice text={c.closed} />}
      <Button asChild variant="outline" className="h-11 self-start">
        <Link href={routes.community(locale, slug)}>{t.own.viewPublic}</Link>
      </Button>

      {manages(role) && active && (
        <section className="flex flex-col gap-4">
          <h2 className="font-semibold">{c.editorEdit}</h2>
          <CommunityEditor
            locale={locale}
            initial={{ slug, name: community.name, description: community.description, area: community.area, locale: community.locale, links: community.links }}
            t={c}
            managePath={routes.manageCommunity(locale, "{slug}")}
          />
        </section>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="font-semibold">{c.membersTitle}</h2>
        <p className="text-xs text-muted-foreground">{c.membersOnly}</p>
        <ul className="flex flex-col gap-2">
          {members.map((member) => (
            <li key={member.userId} className="flex flex-wrap items-center justify-between gap-2 rounded-xl border bg-card px-4 py-3">
              <span className="flex flex-col">
                <span className="font-medium">{member.displayName}</span>
                <span className="text-xs text-muted-foreground">
                  {c[`role${member.role}`]} · {formatDay(member.joinedAt, locale)}
                </span>
              </span>
              {active && member.role !== "OWNER" && (
                <span className="flex flex-wrap gap-2">
                  {role === "OWNER" && member.role === "MEMBER" && form("role", { user: member.userId, role: "ADMIN" }, c.makeAdmin)}
                  {role === "OWNER" && member.role === "ADMIN" && form("role", { user: member.userId, role: "MEMBER" }, c.makeMember)}
                  {(role === "OWNER" || (role === "ADMIN" && member.role === "MEMBER")) &&
                    form("remove", { user: member.userId }, c.remove, "outline", true)}
                </span>
              )}
            </li>
          ))}
        </ul>
      </section>

      {role === "OWNER" && active && (
        <section className="flex flex-col gap-4 border-t pt-6">
          <div className="flex flex-col gap-2">
            <h2 className="font-semibold">{c.transfer}</h2>
            <p className="text-sm text-muted-foreground">{c.transferHint}</p>
            <div className="flex flex-wrap gap-2">
              {members
                .filter((member) => member.role === "ADMIN")
                .map((admin) => (
                  <span key={admin.userId}>{form("transfer", { user: admin.userId }, `${c.transfer}: ${admin.displayName}`)}</span>
                ))}
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <h2 className="font-semibold">{c.close}</h2>
            <p className="text-sm text-muted-foreground">{c.closeConfirm.replace("{n}", String(upcoming))}</p>
            {form("close", {}, c.close, "outline", true)}
          </div>
        </section>
      )}
    </main>
  );
}
