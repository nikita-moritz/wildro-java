import "server-only";

import { NextResponse } from "next/server";

import { siteUrl } from "@/lib/env";
import { isLocale, type Locale } from "@/lib/locales";

/**
 * A 303 to a path of this site. Built on SITE_URL, never on request.url: behind
 * Caddy that is this server's loopback address, and the browser would be sent
 * to localhost.
 */
export function seeOther(path: string): NextResponse {
  return NextResponse.redirect(new URL(path, siteUrl()), { status: 303 });
}

export function localeOf(form: FormData | null): Locale {
  const value = form?.get("locale");
  return typeof value === "string" && isLocale(value) ? value : "de";
}

/**
 * Where a form asked to go back to: a path of this site's own account pages in
 * the form's language, nothing else, so the field cannot send anyone elsewhere.
 */
export function nextPath(form: FormData | null, locale: Locale): string | null {
  const value = text(form, "next");
  return value.startsWith(`/${locale}/account/`) && !value.includes("//") && !value.includes("\\") ? value : null;
}

export function text(form: FormData | null, name: string): string {
  const value = form?.get(name);
  return typeof value === "string" ? value : "";
}
