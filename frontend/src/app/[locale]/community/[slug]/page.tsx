import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { CommunityLogo } from "@/components/community/community-logo";
import { MembershipIsland } from "@/components/community/membership-island";
import { EventListItem, eventCardText } from "@/components/event-list-item";
import { ReportForm } from "@/components/report-form";
import { SiteFooter } from "@/components/site-footer";
import { getCommunity, getCommunityEvents } from "@/lib/communities";
import { getDictionary } from "@/lib/dictionaries";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { alternatesFor } from "@/lib/seo";

export async function generateMetadata({ params }: PageProps<"/[locale]/community/[slug]">): Promise<Metadata> {
  const { locale, slug } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const community = await getCommunity(slug);
  if (!community) {
    notFound();
  }
  return {
    title: `${community.name} · ${community.area}`,
    description: community.description.slice(0, 160),
    alternates: alternatesFor((l) => routes.community(l, slug), locale),
    // A closed group stays reachable for its links, but is no longer worth a search result.
    robots: community.status === "CLOSED" ? { index: false, follow: true } : undefined,
  };
}

export const instant = false;

/**
 * The public page of a community (SPEC.md §18.3): name, area, description,
 * channels, how many members, and its upcoming events. Never the members'
 * names. One cached render for everyone; the join and manage buttons are asked
 * for from the browser.
 */
export default async function CommunityPage({ params }: PageProps<"/[locale]/community/[slug]">) {
  const { locale, slug } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const community = await getCommunity(slug);
  if (!community) {
    notFound();
  }
  const t = getDictionary(locale);
  const events = await getCommunityEvents(community.id, locale);
  const active = community.status === "ACTIVE";
  const foreign = community.locale !== locale;

  return (
    <>
      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-10">
        <BackLink href={routes.communities(locale)} label={t.nav.back} className="-mb-4" />
        <div className="flex items-center gap-4">
          <CommunityLogo url={community.logoUrl} name={community.name} size={72} />
          <div className="flex min-w-0 flex-col gap-2">
            <h1 lang={community.locale} className="text-3xl font-semibold tracking-tight">
              {community.name}
            </h1>
            <p className="text-sm text-muted-foreground">
              {community.area} · {t.community.members.replace("{n}", String(community.memberCount))}
            </p>
          </div>
        </div>

        {!active && (
          <p role="status" className="rounded-xl border bg-muted px-4 py-3 text-sm font-medium">
            {t.community.closed}
          </p>
        )}

        <MembershipIsland
          slug={community.slug}
          locale={locale}
          active={active}
          t={t.community}
          signInHref={routes.signIn(locale)}
          manageHref={routes.manageCommunity(locale, community.slug)}
        />

        <div className="flex flex-col gap-2">
          {foreign && <p className="text-xs text-muted-foreground">{t.event.inOrganizerLanguage}</p>}
          <div lang={community.locale} className="flex flex-col gap-3 text-sm leading-relaxed">
            {community.description.split(/\n{2,}/).map((paragraph, index) => (
              <p key={index} className="whitespace-pre-line">
                {paragraph}
              </p>
            ))}
          </div>
        </div>

        {community.links.length > 0 && (
          <section className="flex flex-col gap-2">
            <h2 className="text-xs font-semibold tracking-widest text-muted-foreground uppercase">{t.community.links}</h2>
            <ul className="flex flex-col gap-1 text-sm">
              {community.links.map((link) => (
                <li key={link}>
                  <a href={link} target="_blank" rel="nofollow noopener noreferrer" className="text-primary underline-offset-2 hover:underline">
                    {new URL(link).host}
                  </a>
                </li>
              ))}
            </ul>
          </section>
        )}

        <section className="flex flex-col gap-3">
          <h2 className="font-semibold">{t.community.upcoming}</h2>
          {events.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t.community.noUpcoming}</p>
          ) : (
            <ul className="flex flex-col gap-3">
              {events.map((event, index) => (
                <EventListItem key={event.slug} event={event} locale={locale} text={eventCardText(t)} index={index} />
              ))}
            </ul>
          )}
        </section>

        {/* Only what can still be taken down is reported: a closed group is read-only. */}
        {active && <ReportForm slug={community.slug} target="community" t={t.report} />}
      </main>
      <SiteFooter locale={locale} t={t} />
    </>
  );
}
