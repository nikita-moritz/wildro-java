import { getCommunity } from "@/lib/communities";
import { eventServiceUrl } from "@/lib/env";
import { isLocale } from "@/lib/locales";

/**
 * GET /{locale}/community/{slug}/calendar.ics: a group's events as a calendar
 * to subscribe to. The calendar app fetches it again by itself, so a new
 * outing appears and a cancelled one shows as cancelled without anyone doing
 * anything. A group taken down by moderation has no calendar, like no page.
 */
export async function GET(_request: Request, { params }: RouteContext<"/[locale]/community/[slug]/calendar.ics">) {
  const { locale, slug } = await params;
  if (!isLocale(locale)) {
    return new Response(null, { status: 404 });
  }
  const community = await getCommunity(slug).catch(() => undefined);
  if (community === undefined) {
    return new Response(null, { status: 503 });
  }
  if (community === null) {
    return new Response(null, { status: 404 });
  }
  const url = new URL(`/v1/events/community/${community.id}/calendar.ics`, eventServiceUrl());
  url.searchParams.set("locale", locale);
  url.searchParams.set("name", `${community.name} · wildro`);
  const response = await fetch(url, { cache: "no-store", signal: AbortSignal.timeout(10000) }).catch(() => null);
  if (!response) {
    return new Response(null, { status: 503 });
  }
  if (!response.ok) {
    return new Response(null, { status: 502 });
  }
  return new Response(await response.text(), {
    headers: {
      "Content-Type": "text/calendar; charset=utf-8",
      // Inline, not attachment: a subscription reads the feed, it does not save a file.
      "Content-Disposition": `inline; filename="${slug.replace(/[^a-z0-9-]/g, "")}.ics"`,
      "Cache-Control": "public, max-age=900",
      "X-Robots-Tag": "noindex",
    },
  });
}
