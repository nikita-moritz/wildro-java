import {
  Anchor,
  Baby,
  Bike,
  Footprints,
  Leaf,
  Mountain,
  Tent,
  TrendingUp,
  Waves,
  Wind,
  type LucideIcon,
} from "lucide-react";

import type { ActivityType, Difficulty } from "@/lib/vocabulary";

/** One icon per activity, the set the working version uses. */
export const ACTIVITY_ICON: Record<ActivityType, LucideIcon> = {
  HIKING: Footprints,
  TRAIL_RUN: Wind,
  CYCLING: Bike,
  WATER: Waves,
  MOUNTAINEERING: Mountain,
  CLIMBING: TrendingUp,
  FERRATA: Anchor,
  KIDS: Baby,
  YOGA: Leaf,
  CAMPING: Tent,
};

/**
 * One colour per activity, for the map's markers and the dots in its list.
 * Plain hex, because the map engine parses colours itself and knows no CSS
 * variables or oklch. Each carries a white icon at 4.5:1 or better.
 */
export const ACTIVITY_COLOR: Record<ActivityType, string> = {
  HIKING: "#15803d",
  TRAIL_RUN: "#c2410c",
  CYCLING: "#1d4ed8",
  WATER: "#0e7490",
  MOUNTAINEERING: "#57534e",
  CLIMBING: "#b91c1c",
  FERRATA: "#6d28d9",
  KIDS: "#be185d",
  YOGA: "#0f766e",
  CAMPING: "#a16207",
};

/**
 * Difficulty colours, defined once. The hues are the working version's; the
 * light-mode text is a shade darker than it used there, because its yellow on
 * pale yellow measured about 1.9:1 and its green 3.1:1, below the 4.5:1 WCAG AA
 * floor for text.
 */
export const DIFFICULTY_STYLE: Record<Difficulty, { dot: string; chip: string }> = {
  EASY: {
    dot: "bg-emerald-500",
    chip: "border-emerald-600/20 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
  },
  MEDIUM: {
    dot: "bg-amber-500",
    chip: "border-amber-600/20 bg-amber-500/10 text-amber-800 dark:text-amber-300",
  },
  HARD: {
    dot: "bg-rose-500",
    chip: "border-rose-600/20 bg-rose-500/10 text-rose-700 dark:text-rose-300",
  },
};
