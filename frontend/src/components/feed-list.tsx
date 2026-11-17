"use client";

import { Loader2 } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import { EventListItem, type EventCardText } from "@/components/event-list-item";
import { FeedPageLink } from "@/components/feed-pagination";
import { Button } from "@/components/ui/button";
import type { EventCard } from "@/lib/events-api";
import type { Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { useMounted } from "@/lib/use-mounted";

export interface FeedListText {
  next: string;
  loading: string;
  loadFailed: string;
  retry: string;
}

/** What /api/feed answers: one more page of the feed. */
export interface FeedChunk {
  items: EventCard[];
  hasNext: boolean;
}

/** How far below the screen the next page starts loading, so the visitor rarely sees it arrive. */
const PRELOAD_MARGIN = "0px 0px 800px 0px";

type Status = "idle" | "loading" | "failed";

/**
 * The events list, growing while the visitor scrolls (SPEC.md §0.3.2).
 *
 * The server renders the first page with a plain link to the next one, which is
 * what crawlers and visitors without JavaScript follow; the `scripted:` variant
 * hides it from the first paint when the script runs. Once hydrated, a sentinel
 * takes its place: when it comes near the screen, the next page comes from
 * /api/feed, the same cached read as ?page=N, and is appended. The address does
 * not change, so Back leads where the visitor came from, and Next keeps this
 * state, scroll position included, when they come back from an event
 * (Activity, see node_modules/next/dist/docs, "Preserving UI state").
 *
 * The page keys this component by filter and page, so a new search or filter
 * starts over from its first page.
 */
export function FeedList({
  locale,
  initialItems,
  page,
  hasNext,
  query,
  words,
  text,
  t,
}: {
  locale: Locale;
  initialItems: EventCard[];
  /** The page the server rendered; the next one loaded is the one after it. */
  page: number;
  hasNext: boolean;
  /** The filter as a query string, without the page. */
  query: string;
  words: string[];
  text: EventCardText;
  t: FeedListText;
}) {
  const enhanced = useMounted();
  // One array per page loaded: the fade-in staggers within each, not across the whole list.
  const [pages, setPages] = useState<EventCard[][]>([initialItems]);
  const [next, setNext] = useState<number | null>(hasNext ? page + 1 : null);
  const [status, setStatus] = useState<Status>("idle");
  const inFlight = useRef(false);
  const sentinel = useRef<HTMLDivElement>(null);

  const load = useCallback(async () => {
    if (next === null || inFlight.current) {
      return;
    }
    inFlight.current = true;
    setStatus("loading");
    try {
      const params = new URLSearchParams(query);
      params.set("locale", locale);
      params.set("page", String(next));
      const response = await fetch(`/api/feed?${params}`);
      if (response.status >= 400 && response.status < 500) {
        // Past the end (the list shrank while it was open) or past the deepest page: nothing more.
        setNext(null);
        setStatus("idle");
        return;
      }
      if (!response.ok) {
        throw new Error(`the feed answered ${response.status}`);
      }
      const more = (await response.json()) as FeedChunk;
      // An event that passed while the list was open moves the pages up by one,
      // so the next page can repeat the last one shown; it is shown once.
      setPages((shown) => {
        const seen = new Set(shown.flat().map((event) => event.slug));
        const fresh = more.items.filter((event) => !seen.has(event.slug));
        return fresh.length > 0 ? [...shown, fresh] : shown;
      });
      setNext(more.hasNext ? next + 1 : null);
      setStatus("idle");
    } catch {
      setStatus("failed");
    } finally {
      inFlight.current = false;
    }
  }, [locale, next, query]);

  // Re-armed after every page: if the sentinel is still near the screen, the next one follows at once.
  useEffect(() => {
    const target = sentinel.current;
    if (!enhanced || target === null || next === null || status !== "idle") {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          void load();
        }
      },
      { rootMargin: PRELOAD_MARGIN },
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [enhanced, load, next, status]);

  return (
    <>
      {/*
        data-feed scopes the `tiles:` variant to this list (globals.css). Tiles fill
        the row at 18rem or more each: one on a phone, four or five on a laptop.
      */}
      <ul
        data-feed
        aria-busy={status === "loading"}
        className="flex flex-col gap-3 tiles:grid tiles:grid-cols-[repeat(auto-fill,minmax(18rem,1fr))]"
      >
        {pages.map((items) =>
          items.map((event, index) => (
            <EventListItem key={event.slug} event={event} locale={locale} text={text} index={index} words={words} />
          )),
        )}
      </ul>

      {next !== null && !enhanced && (
        <FeedPageLink href={routes.feed(locale, next, new URLSearchParams(query))} rel="next" className="self-end scripted:hidden">
          {t.next}
        </FeedPageLink>
      )}

      {next !== null && enhanced && (
        <div ref={sentinel} className="flex min-h-12 flex-col items-center justify-center gap-2 py-2">
          {status === "loading" && <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" aria-hidden />}
          {status === "failed" && (
            <>
              <p className="text-xs text-muted-foreground">{t.loadFailed}</p>
              <Button variant="outline" size="sm" onClick={() => void load()}>
                {t.retry}
              </Button>
            </>
          )}
        </div>
      )}

      {/* Always present, so screen readers hear when loading starts or fails. */}
      <p role="status" className="sr-only">
        {status === "loading" ? t.loading : status === "failed" ? t.loadFailed : ""}
      </p>
    </>
  );
}
