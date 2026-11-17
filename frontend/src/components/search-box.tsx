"use client";

import { Loader2, Search, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState, useTransition } from "react";

import { MAX_QUERY_LENGTH } from "@/lib/feed-filter";

export interface SearchText {
  label: string;
  placeholder: string;
  clear: string;
}

/** How long typing must pause before the list follows. */
const DEBOUNCE_MS = 350;

/**
 * Search over name, organiser and place (SPEC.md §0, поиск). A plain GET form
 * that works without script; with it, the list follows the typing after a short
 * pause, in place, keeping every other filter. The address it builds carries
 * "q", which robots.ts closes to crawlers like every filter parameter.
 *
 * `others` is the current filter without q and without the page: a new search
 * starts at page one.
 */
export function SearchBox({
  action,
  value,
  others,
  t,
}: {
  action: string;
  value: string | null;
  others: string;
  t: SearchText;
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [text, setText] = useState(value ?? "");

  // A new address (back button, a reset in the filters panel) brings its own text.
  const [applied, setApplied] = useState(value ?? "");
  if (applied !== (value ?? "")) {
    setApplied(value ?? "");
    setText(value ?? "");
  }

  function go(next: string) {
    const trimmed = next.trim();
    // Already the address's query: nothing to do, and no extra history entry.
    if (trimmed === (value ?? "")) {
      return;
    }
    const params = new URLSearchParams(others);
    if (trimmed) {
      params.set("q", trimmed);
    }
    const search = params.toString();
    startTransition(() => router.replace(search ? `${action}?${search}` : action, { scroll: false }));
  }

  useEffect(() => {
    const timer = setTimeout(() => go(text), DEBOUNCE_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- go reads the latest props; only typing restarts the pause
  }, [text]);

  return (
    <form
      action={action}
      method="get"
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        go(text);
      }}
      className="relative"
    >
      {[...new URLSearchParams(others)].map(([key, val], index) => (
        <input key={`${key}-${index}`} type="hidden" name={key} value={val} />
      ))}
      <label className="sr-only" htmlFor="event-search">
        {t.label}
      </label>
      <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
      <input
        id="event-search"
        type="search"
        name="q"
        value={text}
        onChange={(event) => setText(event.target.value)}
        maxLength={MAX_QUERY_LENGTH}
        placeholder={t.placeholder}
        autoComplete="off"
        enterKeyHint="search"
        className="h-11 w-full rounded-lg border bg-card pr-10 pl-9 text-base shadow-sm focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none sm:text-sm [&::-webkit-search-cancel-button]:hidden"
      />
      <span className="absolute top-1/2 right-1 flex -translate-y-1/2 items-center">
        {pending ? (
          <Loader2 className="m-2.5 h-4 w-4 animate-spin text-muted-foreground" aria-hidden />
        ) : (
          text && (
            <button
              type="button"
              onClick={() => {
                setText("");
                go("");
              }}
              aria-label={t.clear}
              className="flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground hover:text-foreground"
            >
              <X className="h-4 w-4" />
            </button>
          )
        )}
      </span>
    </form>
  );
}
