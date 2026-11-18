import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";

import { LeaveControl, LifecycleControl, SessionsControl, SignOutControl } from "@/components/auth/account-controls";
import { ElvixShell } from "@/components/auth/elvix-shell";
import { Button } from "@/components/ui/button";
import { getDictionary } from "@/lib/dictionaries";
import { brand, elvixClientId } from "@/lib/env";
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
 * Rendered blocking, per request: a signed-out visitor gets a real redirect to
 * the sign-in page rather than a shell that redirects after it has streamed.
 */
export const instant = false;

export default async function AccountPage({ params, searchParams }: PageProps<"/[locale]/account">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale).auth;
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
  const home = routes.home(locale);
  const notice =
    query.saved === "1" ? t.saved : query.error === "name" ? t.invalidName : query.error === "failed" ? t.failed : null;

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

          <div className="flex flex-col gap-1 border-t pt-3">
            <span className="text-sm font-medium">{t.roles}</span>
            <span className="text-sm">{me.roles.join(", ")}</span>
          </div>
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
            appName={brand().name}
            target={home}
            privacyUrl={routes.privacy(locale)}
            termsUrl={routes.legalNotice(locale)}
          />
        </section>
      </main>
    </ElvixShell>
  );
}
