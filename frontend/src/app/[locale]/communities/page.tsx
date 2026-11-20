import { Plus } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { SiteFooter } from "@/components/site-footer";
import { Button } from "@/components/ui/button";
import { listCommunities } from "@/lib/communities";
import { getDictionary } from "@/lib/dictionaries";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { alternatesFor } from "@/lib/seo";

export async function generateMetadata({ params }: PageProps<"/[locale]/communities">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale).community;
  return { title: t.listTitle, description: t.listLead, alternates: alternatesFor((l) => routes.communities(l), locale) };
}

export const instant = false;

/** All active groups, the largest first (SPEC.md §18.3). Indexable: groups are search traffic. */
export default async function CommunitiesPage({ params, searchParams }: PageProps<"/[locale]/communities">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const query = await searchParams;
  const page = Math.max(1, Math.min(50, Number(query.page) || 1));
  const t = getDictionary(locale);
  const list = await listCommunities(page);

  return (
    <>
      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-10">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="flex flex-col gap-2">
            <h1 className="text-3xl font-semibold tracking-tight">{t.community.listTitle}</h1>
            <p className="text-muted-foreground">{t.community.listLead}</p>
          </div>
          {/* The same for everyone, so the page stays one cached render; signed out, the editor sends on to sign-in. */}
          <Button asChild size="touch">
            <Link href={routes.newCommunity(locale)} prefetch={false}>
              <Plus aria-hidden className="h-4 w-4" />
              {t.community.newCommunity}
            </Link>
          </Button>
        </div>
        {list.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t.community.listEmpty}</p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {list.items.map((community) => (
              <li key={community.slug}>
                <Link
                  href={routes.community(locale, community.slug)}
                  className="flex h-full flex-col gap-1 rounded-xl border bg-card p-4 transition-colors hover:bg-muted"
                >
                  <span lang={community.locale} className="font-medium">
                    {community.name}
                  </span>
                  <span className="text-sm text-muted-foreground">{community.area}</span>
                  <span className="text-xs text-muted-foreground">
                    {t.community.members.replace("{n}", String(community.memberCount))}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
        <nav className="flex gap-3 text-sm">
          {page > 1 && <Link href={routes.communities(locale, page - 1)}>←</Link>}
          {list.hasNext && <Link href={routes.communities(locale, page + 1)}>→</Link>}
        </nav>
      </main>
      <SiteFooter locale={locale} t={t} />
    </>
  );
}
