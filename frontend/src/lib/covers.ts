import type { ActivityType } from "@/lib/vocabulary";

/**
 * The stand-in cover of an activity, shown when an event has no picture of its
 * own, so a card is never an empty box. The photos are the owner's own and are
 * of that kind of outing, not of the event: the page says so beside them.
 * Trail running is the one exception to "own": an Unsplash photo, recorded in
 * ~/deploy/wildro-media/SOURCES.md.
 *
 * They live in the R2 bucket `wildro-media` (EU jurisdiction) behind
 * media.wildro.app, not in this repository. The path carries a version because
 * the files are served as immutable for a year: a changed photo is a new
 * version here, never an overwrite there.
 */
const MEDIA_ORIGIN = "https://media.wildro.app";
const VERSION = "v1";

/**
 * Partial on purpose: an activity added later has no photo until one is chosen,
 * and falls back to the logo tile meanwhile.
 */
const FILE: Partial<Record<ActivityType, string>> = {
  HIKING: "hiking",
  TRAIL_RUN: "trailrun",
  CYCLING: "cycling",
  WATER: "water",
  MOUNTAINEERING: "mountaineering",
  CLIMBING: "climbing",
  FERRATA: "ferrata",
  KIDS: "kids",
  YOGA: "yoga",
  CAMPING: "camping",
};

export interface DefaultCover {
  /** 800 px wide, for a card. */
  small: string;
  /** 1600 px wide, for the event page and link previews. */
  large: string;
  srcSet: string;
  width: 1600;
  height: 900;
}

export function defaultCover(activityType: ActivityType): DefaultCover | null {
  const file = FILE[activityType];
  if (!file) {
    return null;
  }
  const small = `${MEDIA_ORIGIN}/covers/${VERSION}/${file}-800.webp`;
  const large = `${MEDIA_ORIGIN}/covers/${VERSION}/${file}-1600.webp`;
  return { small, large, srcSet: `${small} 800w, ${large} 1600w`, width: 1600, height: 900 };
}
