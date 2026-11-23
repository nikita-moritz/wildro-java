import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { ConfirmForm } from "@/components/confirm-form";
import { CommunityEditor } from "@/components/community/community-editor";
import { CommunityLogo } from "@/components/community/community-logo";
import { Notice } from "@/components/own/notice";
import { OwnEventSections } from "@/components/own/own-event-sections";
import { Button } from "@/components/ui/button";
import { getCommunity, getCommunityEvents, manages, membersOf, standingIn } from "@/lib/communities";
import { communityCode } from "@/lib/community-answer";
import { getDictionary } from "@/lib/dictionaries";
import { formatDay } from "@/lib/format";
import { isLocale } from "@/lib/locales";
import { listOfCommunity } from "@/lib/own-events";
import { requestTime } from "@/lib/request-time";
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
  // The group's whole working list, colleagues' drafts included: for those who may edit them.
  const [groupEvents, now] = manages(role) ? await Promise.all([listOfCommunity(session.jwt, slug), requestTime()]) : [null, 0];
  const upcoming = role === "OWNER" && active ? (await getCommunityEvents(community.id, locale)).length : 0;
  const failed = typeof query.error === "string" ? c.errors[communityCode(query.error)] : null;
  const api = `/api/communities/${encodeURIComponent(slug)}`;
  const form = (action: string, fields: Record<string, string>, label: string, variant: "default" | "outline" = "outline", danger = false) => (
    <form method="post" action={`${api}/${action}`}>
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
      <div className="flex flex-wrap gap-2">
        {manages(role) && active && (
          <Button asChild className="h-11">
            <Link href={routes.newEvent(locale, { group: slug })}>{c.newEventHere}</Link>
          </Button>
        )}
        <Button asChild variant="outline" className="h-11">
          <Link href={routes.community(locale, slug)}>{t.own.viewPublic}</Link>
        </Button>
      </div>

      {groupEvents && (
        <section className="flex flex-col gap-3">
          <h2 className="font-semibold">{c.eventsTitle}</h2>
          <OwnEventSections events={groupEvents} now={now} locale={locale} t={t} empty={c.eventsEmpty} />
        </section>
      )}

      {manages(role) && active && (
        <section className="flex flex-col gap-3">
          <h2 className="font-semibold">{c.logoTitle}</h2>
          <div className="flex items-center gap-4">
            <CommunityLogo url={community.logoUrl} name={community.name} size={80} />
            <p className="text-sm text-muted-foreground">{c.logoHint}</p>
          </div>
          {/* A plain multipart form: the server scales the picture, nothing runs in the browser. */}
          <form
            method="post"
            action={`/api/communities/${encodeURIComponent(slug)}/logo`}
            encType="multipart/form-data"
            className="flex flex-wrap items-center gap-2"
          >
            <input type="hidden" name="locale" value={locale} />
            <input type="hidden" name="intent" value="upload" />
            <label className="flex min-h-11 w-full cursor-pointer items-center rounded-lg border bg-background px-3 text-sm sm:w-auto sm:flex-1">
              <span className="sr-only">{c.logoChoose}</span>
              <input
                type="file"
                name="logo"
                required
                accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
                className="w-full text-sm file:mr-3 file:rounded-full file:border-0 file:bg-muted file:px-3 file:py-1.5 file:text-sm file:font-medium"
              />
            </label>
            <Button type="submit" className="h-11">
              {c.logoUpload}
            </Button>
          </form>
          {community.logoUrl && form("logo", { intent: "remove" }, c.logoRemove, "outline", true)}
        </section>
      )}

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
                  {(role === "OWNER" || (role === "ADMIN" && member.role === "MEMBER")) && (
                    <ConfirmForm
                      action={`${api}/remove`}
                      fields={{ locale, user: member.userId }}
                      label={c.remove}
                      question={c.removeConfirm.replace("{name}", member.displayName)}
                      yes={c.removeYes}
                      size="sm"
                    />
                  )}
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
                  <ConfirmForm
                    key={admin.userId}
                    action={`${api}/transfer`}
                    fields={{ locale, user: admin.userId }}
                    label={`${c.transfer}: ${admin.displayName}`}
                    question={c.transferConfirm.replace("{name}", admin.displayName)}
                    yes={c.transferYes}
                  />
                ))}
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <h2 className="font-semibold">{c.close}</h2>
            <p className="text-sm text-muted-foreground">{c.closeConfirm.replace("{n}", String(upcoming))}</p>
            <ConfirmForm
              action={`${api}/close`}
              fields={{ locale }}
              label={c.close}
              question={c.closeQuestion}
              yes={c.closeYes}
            />
          </div>
        </section>
      )}
    </main>
  );
}
