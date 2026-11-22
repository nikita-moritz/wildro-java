import { eventServiceUrl } from "@/lib/env";
import { isLocale } from "@/lib/locales";

/**
 * GET /{locale}/event/{slug}/calendar.ics: the event as a calendar file, for
 * Apple's Calendar, Outlook and the rest. The event service writes it (RFC
 * 5545); this hands it on under the site's address, so a phone opens it straight
 * in its calendar. Same visibility as the page: a draft is not there.
 */
export async function GET(_request: Request, { params }: RouteContext<"/[locale]/event/[slug]/calendar.ics">) {
  const { locale, slug } = await params;
  if (!isLocale(locale)) {
    return new Response(null, { status: 404 });
  }
  const url = new URL(`/v1/events/${encodeURIComponent(slug)}/calendar.ics`, eventServiceUrl());
  url.searchParams.set("locale", locale);
  const response = await fetch(url, { cache: "no-store", signal: AbortSignal.timeout(10000) }).catch(() => null);
  if (!response) {
    return new Response(null, { status: 503 });
  }
  if (!response.ok) {
    return new Response(null, { status: response.status === 404 ? 404 : 502 });
  }
  return new Response(await response.text(), {
    headers: {
      "Content-Type": "text/calendar; charset=utf-8",
      "Content-Disposition": `attachment; filename="${slug.replace(/[^a-z0-9-]/g, "")}.ics"`,
      "Cache-Control": "public, max-age=300",
      "X-Robots-Tag": "noindex",
    },
  });
}
