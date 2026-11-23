import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { CommunityLogo } from "@/components/community/community-logo";
import { EventListItem, eventCardText } from "@/components/event-list-item";
import { Notice } from "@/components/own/notice";
import { OwnEventSections } from "@/components/own/own-event-sections";
import { Button } from "@/components/ui/button";
import { getCommunity, getCommunityEvents, manages, myCommunities, type Standing } from "@/lib/communities";
import { getDictionary, type Dictionary } from "@/lib/dictionaries";
import { isLocale, type Locale } from "@/lib/locales";
import { listMine } from "@/lib/own-events";
import { requestTime } from "@/lib/request-time";
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

/**
 * Everything of one person in one place (owner's call, 2026-11-22): the events
 * they wrote, what is coming up in their groups, the groups they run and the
 * groups they are in. The group parts are built from the same cached public
 * reads as the group pages; only the list of memberships is per person.
 */
export default async function MyEventsPage({ params }: PageProps<"/[locale]/account/events">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const session = await requireSignedIn(locale, routes.myEvents(locale));
  const [events, standings] = session ? await Promise.all([listMine(session.jwt), myCommunities(session.jwt)]) : [null, null];
  const groups = (standings ?? []).filter((standing) => standing.status === "ACTIVE");
  const mine = new Set((events ?? []).map((event) => event.slug));
  // Upcoming events of every group, soonest first; what the person wrote is already above.
  const groupEvents = (await Promise.all(groups.map((group) => getCommunityEvents(group.id, locale).catch(() => []))))
    .flat()
    .filter((event) => !mine.has(event.slug))
    .sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt));
  const logos = new Map(
    await Promise.all(
      groups.map(async (group) => [group.slug, (await getCommunity(group.slug).catch(() => null))?.logoUrl ?? null] as const),
    ),
  );
  const now = await requestTime();
  const leading = groups.filter((group) => manages(group.role));
  const member = groups.filter((group) => !manages(group.role));

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-8 px-4 py-10">
      <BackLink href={routes.account(locale)} label={t.own.back} />
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">{t.own.myEventsTitle}</h1>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" className="h-11">
              <Link href={routes.newCommunity(locale)}>{t.community.newCommunity}</Link>
            </Button>
            <Button asChild className="h-11">
              <Link href={routes.newEvent(locale)}>{t.own.newEvent}</Link>
            </Button>
          </div>
        </div>
        <p className="text-sm text-muted-foreground">{t.own.myEventsLead}</p>
      </div>

      {events === null || standings === null ? <Notice text={t.auth.unavailable} tone="alert" /> : null}

      <Section title={t.own.sectionMine}>
        {events && <OwnEventSections events={events} now={now} locale={locale} t={t} empty={t.own.empty} />}
      </Section>

      <Section title={t.own.sectionGroupEvents} count={groupEvents.length}>
        {groupEvents.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t.own.groupEventsEmpty}</p>
        ) : (
          <ul className="flex flex-col gap-3">
            {groupEvents.map((event, index) => (
              <EventListItem key={event.slug} event={event} locale={locale} text={eventCardText(t)} index={index} />
            ))}
          </ul>
        )}
      </Section>

      <Section title={t.own.sectionLeading} count={leading.length}>
        <GroupList groups={leading} logos={logos} locale={locale} t={t} manage />
      </Section>

      <Section title={t.own.sectionMember} count={member.length}>
        <GroupList groups={member} logos={logos} locale={locale} t={t} />
      </Section>

      {standings !== null && groups.length === 0 && (
        <div className="flex flex-col items-start gap-2">
          <p className="text-sm text-muted-foreground">{t.own.groupsEmpty}</p>
          <Button asChild variant="outline" className="h-11">
            <Link href={routes.communities(locale)}>{t.own.findGroups}</Link>
          </Button>
        </div>
      )}
    </main>
  );
}

function Section({ title, count, children }: { title: string; count?: number; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="flex items-center gap-2 font-semibold">
        {title}
        {count !== undefined && count > 0 && <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">{count}</span>}
      </h2>
      {children}
    </section>
  );
}

/** Groups as rows: picture, name, role; those the person runs lead to their settings. */
function GroupList({
  groups,
  logos,
  locale,
  t,
  manage = false,
}: {
  groups: Standing[];
  logos: Map<string, string | null>;
  locale: Locale;
  t: Dictionary;
  manage?: boolean;
}) {
  if (groups.length === 0) {
    return null;
  }
  return (
    <ul className="grid gap-3 sm:grid-cols-2">
      {groups.map((group) => (
        <li key={group.slug}>
          <Link
            href={manage ? routes.manageCommunity(locale, group.slug) : routes.community(locale, group.slug)}
            className="flex h-full items-center gap-3 rounded-xl border bg-card p-4 transition-colors hover:bg-muted"
          >
            <CommunityLogo url={logos.get(group.slug) ?? null} name={group.name} size={40} />
            <span className="flex min-w-0 flex-col">
              <span className="truncate font-medium">{group.name}</span>
              {group.role && <span className="text-xs text-muted-foreground">{t.community[`role${group.role}`]}</span>}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
