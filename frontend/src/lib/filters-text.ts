import type { FiltersText } from "@/components/filters-panel";
import type { Dictionary } from "@/lib/dictionaries";
import type { FeedFilter } from "@/lib/feed-filter";

/** The strings the client-side filters panel needs, strings only, since functions cannot cross into it. */
export function filtersText(t: Dictionary): FiltersText {
  return { ...t.filters, activityLabels: t.activity, difficultyLabels: t.difficulty, priceLabels: t.price };
}

/** How many filter groups are set, for the badge on the Filters button. */
export function activeFilterCount(filter: FeedFilter): number {
  return [
    filter.types.length > 0,
    filter.difficulties.length > 0,
    filter.from !== null || filter.to !== null,
    filter.minKm !== null || filter.maxKm !== null,
    filter.near !== null,
    filter.price !== null,
    filter.communities.length > 0,
  ].filter(Boolean).length;
}
