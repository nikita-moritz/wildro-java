/**
 * The languages of the site. The same three as the event service's
 * Event.LOCALES: every event is written in all of them, so a page exists in
 * every language or not at all. Two services, two lists, one fact; if one
 * changes, the other must, and the service refuses a locale it does not know.
 */
export const LOCALES = ["de", "en", "ru"] as const;

export type Locale = (typeof LOCALES)[number];

/** German is the primary market and the x-default of every page. */
export const DEFAULT_LOCALE: Locale = "de";

export function isLocale(value: string): value is Locale {
  return (LOCALES as readonly string[]).includes(value);
}

/** Formatting locales. en-GB rather than en-US: day before month, 24-hour clock, as in the rest of Europe. */
export const INTL_LOCALE: Record<Locale, string> = {
  de: "de-DE",
  en: "en-GB",
  ru: "ru-RU",
};

/**
 * Every date on the site is shown in the product's zone, never the server's or
 * the visitor's: an outing starts at 9:00 in the Eifel whatever timezone the
 * person reading about it is in, and the server rendering it may sit in UTC.
 */
export const TIME_ZONE = "Europe/Berlin";
