import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { Notice } from "@/components/own/notice";
import { Button } from "@/components/ui/button";
import { myCommunities } from "@/lib/communities";
import { getDictionary } from "@/lib/dictionaries";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { requireSignedIn } from "@/lib/signed-in-page";

export async function generateMetadata({ params }: PageProps<"/[locale]/account/communities">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return { title: getDictionary(locale).community.myTitle, robots: { index: false, follow: false } };
}

export const instant = false;

export default async function MyCommunitiesPage({ params }: PageProps<"/[locale]/account/communities">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const session = await requireSignedIn(locale);
  const mine = session ? await myCommunities(session.jwt) : null;

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-4 py-10">
      <BackLink href={routes.account(locale)} label={t.community.back} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">{t.community.myTitle}</h1>
        <Button asChild className="h-11">
          <Link href={routes.newCommunity(locale)}>{t.community.newCommunity}</Link>
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">{t.community.myLead}</p>
      {mine === null ? (
        <Notice text={t.auth.unavailable} tone="alert" />
      ) : mine.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t.community.myEmpty}</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {mine.map((community) => (
            <li key={community.slug}>
              <Link
                href={routes.manageCommunity(locale, community.slug)}
                className="flex items-center justify-between gap-2 rounded-xl border bg-card p-4 transition-colors hover:bg-muted"
              >
                <span className="font-medium">{community.name}</span>
                <span className="rounded-full border px-2.5 py-0.5 text-xs">
                  {community.role ? t.community[`role${community.role}`] : ""}
                  {community.status === "CLOSED" ? ` · ${t.community.closed.split(".")[0]}` : ""}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
