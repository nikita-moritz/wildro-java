import "server-only";

import type { Metadata } from "next";

import { siteUrl } from "@/lib/env";
import { DEFAULT_LOCALE, LOCALES, type Locale } from "@/lib/locales";

/**
 * Canonical and hreflang for a page that exists in every language. Every page
 * names itself as canonical and lists all three languages plus x-default, which
 * points at German, the primary market. Set per page, never in a layout: a
 * layout does not know the full address.
 */
export function alternatesFor(path: (locale: Locale) => string, locale: Locale): Metadata["alternates"] {
  const site = siteUrl();
  const languages: Record<string, string> = Object.fromEntries(
    LOCALES.map((each) => [each, `${site}${path(each)}`]),
  );
  languages["x-default"] = `${site}${path(DEFAULT_LOCALE)}`;
  return { canonical: `${site}${path(locale)}`, languages };
}

export function absoluteUrl(path: string): string {
  return `${siteUrl()}${path}`;
}

/**
 * JSON for a script tag. "<" is escaped, because event names come from outside:
 * a title containing "</script>" closed the tag and was stored XSS in the
 * working version.
 */
export function serialiseJsonLd(data: object): string {
  return JSON.stringify(data).replace(/</g, "\\u003c");
}
