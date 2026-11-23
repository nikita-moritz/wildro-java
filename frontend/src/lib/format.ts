import { INTL_LOCALE, TIME_ZONE, type Locale } from "@/lib/locales";

/**
 * Dates as the page's language writes them, always in Europe/Berlin. The
 * working version mixed an English date with a Russian country name on one
 * card; everything here follows the page language.
 */

function dayKey(date: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

export function formatDate(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(INTL_LOCALE[locale], {
    timeZone: TIME_ZONE,
    weekday: "short",
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(new Date(iso));
}

/**
 * A plain date without the weekday, for "last checked". Note that some locales
 * end it with their own abbreviation (ru: "2026 г."), so a sentence around it
 * must not add a full stop of its own.
 */
export function formatDay(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(INTL_LOCALE[locale], {
    timeZone: TIME_ZONE,
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(new Date(iso));
}

/** "3 Oct", for the date chip on a card. */
export function formatShortDate(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(INTL_LOCALE[locale], {
    timeZone: TIME_ZONE,
    day: "numeric",
    month: "short",
  }).format(new Date(iso));
}

/** "Sa., 28. Nov.", for a line in a group's digest: the weekday matters, the year does not. */
export function formatWeekdayDate(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(INTL_LOCALE[locale], {
    timeZone: TIME_ZONE,
    weekday: "short",
    day: "numeric",
    month: "short",
  }).format(new Date(iso));
}

/** "3 Oct 2026", for a list row. */
export function formatMediumDate(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(INTL_LOCALE[locale], {
    timeZone: TIME_ZONE,
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(iso));
}

/**
 * "Sa., 10. Okt. · 11:00" for a one-day outing, "Do., 29. Okt. - So., 1. Nov."
 * for one over several days, for the rows beside the map. The range is left to
 * the locale's own formatRange, which knows where each language drops the
 * repeated month.
 */
export function formatShortSpan(startsAt: string, endsAt: string | null, locale: Locale): string {
  const format = new Intl.DateTimeFormat(INTL_LOCALE[locale], {
    timeZone: TIME_ZONE,
    weekday: "short",
    day: "numeric",
    month: "short",
  });
  if (endsAt && !sameDay(startsAt, endsAt)) {
    // formatRange joins with an en dash, which the house style does not use anywhere.
    return format.formatRange(new Date(startsAt), new Date(endsAt)).replace(/\s*[\u2013\u2014]\s*/g, " - ");
  }
  return `${format.format(new Date(startsAt))} · ${formatTime(startsAt, locale)}`;
}

/** Whether two instants fall on the same calendar day in the product's zone. */
export function sameDay(a: string, b: string): boolean {
  return dayKey(new Date(a)) === dayKey(new Date(b));
}

export function formatTime(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(INTL_LOCALE[locale], {
    timeZone: TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(iso));
}

/**
 * "Sat, 3 October 2026, 10:00 to 14:00", or with both dates when it spans days.
 * The separator word comes from the dictionary so no dash ends up in the copy.
 */
export function formatSchedule(startsAt: string, endsAt: string | null, locale: Locale, until: string): string {
  const start = `${formatDate(startsAt, locale)}, ${formatTime(startsAt, locale)}`;
  if (!endsAt) {
    return start;
  }
  if (dayKey(new Date(startsAt)) === dayKey(new Date(endsAt))) {
    return `${start} ${until} ${formatTime(endsAt, locale)}`;
  }
  return `${start} ${until} ${formatDate(endsAt, locale)}, ${formatTime(endsAt, locale)}`;
}

export function formatNumber(value: number, locale: Locale, maximumFractionDigits = 1): string {
  return new Intl.NumberFormat(INTL_LOCALE[locale], { maximumFractionDigits }).format(value);
}

/** The words for a count in each grammatical number the language has; {n} is the count. */
export type CountForms = { one: string; few?: string; many?: string; other: string };

/** "1 Mitglied", "5 Mitglieder", "2 участника": the form the language wants for this number. */
export function formatCount(forms: CountForms, n: number, locale: Locale): string {
  const rule = new Intl.PluralRules(INTL_LOCALE[locale]).select(n);
  const form = rule === "one" || rule === "few" || rule === "many" ? (forms[rule] ?? forms.other) : forms.other;
  return form.replace("{n}", formatNumber(n, locale, 0));
}
