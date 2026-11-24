import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { connection } from "next/server";

import { ElvixShell } from "@/components/auth/elvix-shell";
import { SignInPanel } from "@/components/auth/sign-in-panel";
import { getDictionary } from "@/lib/dictionaries";
import { elvixClientId } from "@/lib/env";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { returnPath } from "@/lib/signed-in-page";

export async function generateMetadata({ params }: PageProps<"/[locale]/sign-in">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return { title: getDictionary(locale).auth.signInTitle, robots: { index: false, follow: false } };
}

export const instant = false;

/**
 * Also the address elvix sends the browser back to after Google, one per
 * language, as registered in the elvix application: the form mounted here picks
 * the returning sign-in up and finishes it.
 */
export default async function SignInPage({ params, searchParams }: PageProps<"/[locale]/sign-in">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale).auth;
  // Back to where the person was headed (the event editor, say), or the account.
  const next = returnPath(locale, (await searchParams).next);
  // The clientId is configuration of the running server, not of the build: CI
  // builds without it, and a prerender would bake "not available" in for good.
  await connection();
  const clientId = elvixClientId();
  return (
    <main className="mx-auto flex w-full max-w-md flex-1 flex-col gap-6 px-4 py-10">
      <div className="flex flex-col gap-2 text-center">
        <h1 className="text-2xl font-semibold tracking-tight">{t.signInTitle}</h1>
        <p className="text-sm text-muted-foreground">{t.signInLead}</p>
      </div>
      {clientId ? (
        <ElvixShell clientId={clientId} locale={locale}>
          <SignInPanel
            next={next}
            privacyUrl={routes.privacy(locale)}
            termsUrl={routes.terms(locale)}
            failedText={t.failed}
          />
        </ElvixShell>
      ) : (
        <p role="alert" className="rounded-lg bg-muted px-4 py-3 text-center text-sm">
          {t.unavailable}
        </p>
      )}
    </main>
  );
}
