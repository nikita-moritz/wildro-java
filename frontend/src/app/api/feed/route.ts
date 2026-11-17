import type { FeedChunk } from "@/components/feed-list";
import { getFeed } from "@/lib/events-api";
import { paramsOf, parseFilter } from "@/lib/feed-filter";
import { parseFeedPage } from "@/lib/feed-page";
import { isLocale } from "@/lib/locales";

/**
 * One more page of the feed, for the list that grows while scrolling (SPEC.md
 * §0.3.2). The same cached read as /[locale]/events?page=N, so scrolling asks
 * the service for nothing a visit to that page would not. The page's own
 * address stays what crawlers and visitors without JavaScript follow; robots.ts
 * keeps crawlers out of /api/.
 *
 * A page past the end answers 404, as the page itself does, and the list stops.
 */
export async function GET(request: Request): Promise<Response> {
  const query = new URL(request.url).searchParams;
  const locale = query.get("locale") ?? "";
  const page = parseFeedPage(query.get("page") ?? "");
  if (!isLocale(locale) || page === null || page < 2) {
    return Response.json({ error: "expected locale and a page from 2 on" }, { status: 400 });
  }

  const feed = await getFeed(locale, page, parseFilter(paramsOf(query)));
  if (feed.items.length === 0) {
    return Response.json({ error: "past the end of the feed" }, { status: 404 });
  }
  const chunk: FeedChunk = { items: feed.items, hasNext: feed.hasNext };
  return Response.json(chunk, { headers: { "Cache-Control": "no-store" } });
}
