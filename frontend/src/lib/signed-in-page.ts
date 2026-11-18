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
export async function requireSignedIn(locale: Locale): Promise<{ me: Me; jwt: string } | null> {
  const session = await currentSession();
  if (session.state === "signed-out") {
    redirect(routes.signIn(locale));
  }
  return session.state === "signed-in" ? { me: session.me, jwt: session.jwt } : null;
}
