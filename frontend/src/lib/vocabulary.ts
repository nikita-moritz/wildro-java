/**
 * The service's vocabularies, as this frontend knows them. Shared by server
 * and browser code, so it imports nothing server-only. The event service's
 * enums are the source of truth; a value added there and missing here fails the
 * zod parse in events-api.ts with a message rather than rendering blank.
 */
export const ACTIVITY_TYPES = [
  "HIKING",
  "TRAIL_RUN",
  "CYCLING",
  "WATER",
  "MOUNTAINEERING",
  "CLIMBING",
  "FERRATA",
  "KIDS",
  "YOGA",
  "CAMPING",
] as const;

export const DIFFICULTIES = ["EASY", "MEDIUM", "HARD"] as const;

export type ActivityType = (typeof ACTIVITY_TYPES)[number];
export type Difficulty = (typeof DIFFICULTIES)[number];
