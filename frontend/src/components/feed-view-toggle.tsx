"use client";

import { LayoutGrid, List, type LucideIcon } from "lucide-react";
import { useSyncExternalStore } from "react";

import { getFeedView, setFeedView, subscribeFeedView, type FeedView } from "@/lib/feed-view";
import { cn } from "@/lib/utils";

export interface FeedViewText {
  label: string;
  list: string;
  tiles: string;
}

const SERVER_VIEW = (): FeedView => "list";

/**
 * List or tiles (SPEC.md §0.3.2). Pressing a button sets the attribute on
 * <html> and the cards follow by CSS (lib/feed-view.ts). Which button looks
 * pressed comes from the same attribute through the `tiles-chosen:` variant, so it is
 * right from the first paint; aria-pressed follows once hydrated. Hidden until
 * the layout's script has run (`scripted:`), since without it it does nothing.
 */
export function FeedViewToggle({ t }: { t: FeedViewText }) {
  const view = useSyncExternalStore(subscribeFeedView, getFeedView, SERVER_VIEW);

  return (
    <div
      role="group"
      aria-label={t.label}
      className="hidden h-11 shrink-0 items-center gap-0.5 rounded-lg border bg-card p-1 shadow-sm scripted:flex"
    >
      <Choice
        value="list"
        view={view}
        label={t.list}
        icon={List}
        className="bg-muted text-foreground tiles-chosen:bg-transparent tiles-chosen:text-muted-foreground"
      />
      <Choice
        value="tiles"
        view={view}
        label={t.tiles}
        icon={LayoutGrid}
        className="tiles-chosen:bg-muted tiles-chosen:text-foreground"
      />
    </div>
  );
}

function Choice({
  value,
  view,
  label,
  icon: Icon,
  className,
}: {
  value: FeedView;
  view: FeedView;
  label: string;
  icon: LucideIcon;
  className: string;
}) {
  return (
    <button
      type="button"
      aria-pressed={view === value}
      aria-label={label}
      title={label}
      onClick={() => setFeedView(value)}
      className={cn(
        "flex h-9 w-10 items-center justify-center rounded-md text-muted-foreground transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none",
        className,
      )}
    >
      <Icon className="h-4 w-4" />
    </button>
  );
}
