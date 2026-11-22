import "server-only";

import { redirect } from "next/navigation";

import type { Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { currentSession, type Me } from "@/lib/session";

/**
 * The session a personal page needs: a signed-out visitor goes to the sign-in
 * page, a failure to read the session comes back as null and the page says the
 * service is unavailable.
 */
export async function requireSignedIn(locale: Locale, back?: string): Promise<{ me: Me; jwt: string } | null> {
  const session = await currentSession();
  if (session.state === "signed-out") {
    redirect(routes.signIn(locale, back));
  }
  return session.state === "signed-in" ? { me: session.me, jwt: session.jwt } : null;
}

/**
 * Where the sign-in page sends someone afterwards: the page they came from when
 * it is one of this site's, the account otherwise. Only "/<locale>/..." paths
 * pass, so the parameter can never send anyone to another site.
 */
export function returnPath(locale: Locale, next: unknown): string {
  return typeof next === "string" && /^\/(de|en|ru)(\/[A-Za-z0-9_-]+)*$/.test(next) ? next : routes.account(locale);
}
