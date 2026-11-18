import { TIME_ZONE } from "@/lib/locales";

/**
 * A datetime-local field holds a wall-clock time with no zone. Every event time
 * is Europe/Berlin (lib/locales.ts), so the field is read and written in that
 * zone, whatever zone the browser happens to be in.
 */

function parts(date: Date): Record<string, number> {
  const formatted = new Intl.DateTimeFormat("en-GB", {
    timeZone: TIME_ZONE,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).formatToParts(date);
  return Object.fromEntries(formatted.filter((p) => p.type !== "literal").map((p) => [p.type, Number(p.value)]));
}

/** "2026-11-25T09:30" in Berlin to an ISO instant, or null for an empty or broken value. */
export function berlinToIso(local: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(local);
  if (!match) {
    return null;
  }
  const [, y, mo, d, h, mi] = match.map(Number);
  const asUtc = Date.UTC(y, mo - 1, d, h, mi);
  // Berlin's offset at that moment: what Berlin's clock reads at asUtc, minus asUtc.
  const p = parts(new Date(asUtc));
  const offset = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute) - asUtc;
  return new Date(asUtc - offset).toISOString();
}

/** An ISO instant as a datetime-local value in Berlin. */
export function isoToBerlin(iso: string | null): string {
  if (!iso) {
    return "";
  }
  const p = parts(new Date(iso));
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${p.year}-${pad(p.month)}-${pad(p.day)}T${pad(p.hour)}:${pad(p.minute)}`;
}
