import { NextResponse } from "next/server";

import { siteUrl } from "@/lib/env";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { callUserService, currentSession, fromThisSite } from "@/lib/session";

/**
 * POST /api/account/display-name, a plain form post from the account page, so
 * renaming works without JavaScript. Answers with a 303 back to the page,
 * carrying the outcome in the address. The target is built on SITE_URL, never
 * on request.url: behind Caddy that is this server's own loopback address, and
 * the browser was sent to localhost.
 */
export async function POST(request: Request): Promise<Response> {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const form = await request.formData().catch(() => null);
  const requested = form?.get("locale");
  const locale = typeof requested === "string" && isLocale(requested) ? requested : "de";
  const back = (outcome: string) =>
    NextResponse.redirect(new URL(`${routes.account(locale)}?${outcome}`, siteUrl()), { status: 303 });

  const session = await currentSession();
  if (session.state === "signed-out") {
    return NextResponse.redirect(new URL(routes.signIn(locale), siteUrl()), { status: 303 });
  }
  if (session.state === "unavailable") {
    return back("error=failed");
  }
  const name = form?.get("displayName");
  const response = await callUserService("PUT", "/v1/me/display-name", session.jwt, {
    displayName: typeof name === "string" ? name : "",
  });
  if (response?.status === 422) {
    return back("error=name");
  }
  return back(response?.ok ? "saved=1" : "error=failed");
}
