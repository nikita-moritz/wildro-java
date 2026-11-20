import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { CommunityEditor } from "@/components/community/community-editor";
import { Notice } from "@/components/own/notice";
import { getDictionary } from "@/lib/dictionaries";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { requireSignedIn } from "@/lib/signed-in-page";

export async function generateMetadata({ params }: PageProps<"/[locale]/account/communities/new">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return { title: getDictionary(locale).community.editorNew, robots: { index: false, follow: false } };
}

export const instant = false;

export default async function NewCommunityPage({ params }: PageProps<"/[locale]/account/communities/new">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const session = await requireSignedIn(locale);
  const blocker = !session
    ? t.auth.unavailable
    : !session.me.terms.upToDate
      ? t.community.errors.TERMS_REQUIRED
      : !session.me.displayName
        ? t.community.errors.DISPLAY_NAME_REQUIRED
        : null;
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-6 px-4 py-10">
      <BackLink href={routes.myCommunities(locale)} label={t.community.back} />
      <h1 className="text-2xl font-semibold tracking-tight">{t.community.editorNew}</h1>
      {blocker ? (
        <Notice text={blocker} tone="alert" />
      ) : (
        <CommunityEditor locale={locale} initial={null} t={t.community} managePath={routes.manageCommunity(locale, "{slug}")} />
      )}
    </main>
  );
}
