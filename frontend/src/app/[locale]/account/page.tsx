import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";

import { LeaveControl, LifecycleControl, SessionsControl, SignOutControl } from "@/components/auth/account-controls";
import { ElvixShell } from "@/components/auth/elvix-shell";
import { Button } from "@/components/ui/button";
import { getDictionary } from "@/lib/dictionaries";
import { formatDay } from "@/lib/format";
import { moderates } from "@/lib/own-events";
import { TERMS_VERSION } from "@/lib/terms";
import { elvixClientId } from "@/lib/env";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { currentSession } from "@/lib/session";

export async function generateMetadata({ params }: PageProps<"/[locale]/account">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return { title: getDictionary(locale).auth.accountTitle, robots: { index: false, follow: false } };
}

/**
 * Rendered per request, metadata in <head>. A signed-out visitor is sent on to
 * the sign-in page by the redirect Next streams into the response: a browser
 * follows it at once, and crawlers are kept out by robots.txt and noindex, so
 * an HTTP 307 would buy nothing worth a proxy on every request.
 */
export const instant = false;

export default async function AccountPage({ params, searchParams }: PageProps<"/[locale]/account">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const dictionary = getDictionary(locale);
  const t = dictionary.auth;
  const own = dictionary.own;
  const query = await searchParams;
  const session = await currentSession();
  if (session.state === "signed-out") {
    redirect(routes.signIn(locale));
  }
  const clientId = elvixClientId();
  if (session.state === "unavailable" || !clientId) {
    return (
      <main className="mx-auto w-full max-w-xl flex-1 px-4 py-10">
        <p role="alert" className="rounded-lg bg-muted px-4 py-3 text-sm">
          {t.unavailable}
        </p>
      </main>
    );
  }

  const { me } = session;
  const special = me.roles.filter((role): role is keyof typeof t.roleNames => role in t.roleNames).map((role) => t.roleNames[role]);
  const home = routes.home(locale);
  const notice =
    query.saved === "1" || query.terms === "1"
      ? t.saved
      : query.error === "name"
        ? t.invalidName
        : query.error === "failed"
          ? t.failed
          : null;

  return (
    <ElvixShell clientId={clientId} locale={locale}>
      <LifecycleControl target={routes.signIn(locale)} />
      <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-8 px-4 py-10">
        <div className="flex items-center justify-between gap-4">
          <h1 className="text-2xl font-semibold tracking-tight">{t.accountTitle}</h1>
          <SignOutControl label={t.signOut} target={home} />
        </div>

        {notice && (
          <p role="status" className="rounded-lg bg-muted px-4 py-3 text-sm">
            {notice}
          </p>
        )}

        <section className="flex flex-col gap-3 rounded-2xl border bg-card p-5">
          <form method="post" action="/api/account/display-name" className="flex flex-col gap-2">
            <input type="hidden" name="locale" value={locale} />
            <label htmlFor="displayName" className="text-sm font-medium">
              {t.displayName}
            </label>
            <div className="flex flex-col gap-2 sm:flex-row">
              <input
                id="displayName"
                name="displayName"
                defaultValue={me.displayName ?? ""}
                placeholder={me.displayName ? undefined : t.displayNameMissing}
                required
                minLength={2}
                maxLength={50}
                autoComplete="nickname"
                className="h-11 flex-1 rounded-lg border bg-background px-3 text-base"
              />
              <Button type="submit" className="h-11">
                {t.save}
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">{t.displayNameHint}</p>
          </form>

          <div className="flex flex-col gap-1 border-t pt-3">
            <span className="text-sm font-medium">{t.email}</span>
            <span className="text-sm break-all">{me.email}</span>
            <span className="text-xs text-muted-foreground">{t.emailNote}</span>
          </div>

          {/* Only roles that change something on the site; everyone has the plain one. */}
          {special.length > 0 && (
            <div className="flex flex-col gap-1 border-t pt-3">
              <span className="text-sm font-medium">{t.roles}</span>
              <span className="text-sm">{special.join(", ")}</span>
            </div>
          )}
        </section>

        <section className="flex flex-col gap-4 rounded-2xl border bg-card p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="font-semibold">{own.myEventsTitle}</h2>
            <div className="flex gap-2">
              {moderates(me.roles) && (
                <Button asChild variant="outline" size="sm">
                  <Link href={routes.moderation(locale)}>{own.moderationLink}</Link>
                </Button>
              )}
              <Button asChild variant="outline" size="sm">
                <Link href={routes.myCommunities(locale)}>{dictionary.community.myTitle}</Link>
              </Button>
              <Button asChild size="sm">
                <Link href={routes.myEvents(locale)}>{own.myEventsTitle}</Link>
              </Button>
            </div>
          </div>

          {/* Blank while no terms are in force: there is nothing to accept yet. */}
          {me.terms.current && (
            <div className="flex flex-col gap-2 border-t pt-4 text-sm">
              <span className="font-medium">{own.termsTitle}</span>
              {me.terms.upToDate && me.terms.acceptedAt ? (
                <span className="text-muted-foreground">
                  {own.termsAccepted} {formatDay(me.terms.acceptedAt, locale)}.{" "}
                  <Link href={routes.terms(locale)} className="text-primary underline-offset-2 hover:underline">
                    {own.termsRead}
                  </Link>
                </span>
              ) : (
                <form method="post" action="/api/account/terms" className="flex flex-col gap-3">
                  <input type="hidden" name="locale" value={locale} />
                  {/* The version this page knows; the service refuses a mismatch rather than record consent to unseen text. */}
                  <input type="hidden" name="version" value={TERMS_VERSION} />
                  <span className="text-muted-foreground">
                    {own.termsNeeded}{" "}
                    <Link href={routes.terms(locale)} className="text-primary underline-offset-2 hover:underline">
                      {own.termsRead}
                    </Link>
                  </span>
                  <Button type="submit" className="h-11 self-start">
                    {own.termsAccept}
                  </Button>
                </form>
              )}
            </div>
          )}
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="font-semibold">{t.sessionsTitle}</h2>
          <p className="text-sm text-muted-foreground">{t.sessionsLead}</p>
          <SessionsControl target={home} />
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="font-semibold">{t.leaveTitle}</h2>
          <p className="text-sm text-muted-foreground">{t.leaveLead}</p>
          <LeaveControl
            appName="wildro"
            target={home}
            privacyUrl={routes.privacy(locale)}
            termsUrl={routes.terms(locale)}
          />
        </section>
      </main>
    </ElvixShell>
  );
}
