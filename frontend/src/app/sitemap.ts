import type { MetadataRoute } from "next";

import { siteUrl } from "@/lib/env";
import { LOCALES, type Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";

/**
 * The indexable surface of stage one: the home page and the feed in every
 * language, each with its alternates. Event pages join once the event service
 * has an index endpoint for them (AGENTS.md, frontend, not built yet).
 */
export default function sitemap(): MetadataRoute.Sitemap {
  const site = siteUrl();
  const pages: Array<(locale: Locale) => string> = [routes.home, (locale) => routes.feed(locale)];
  return pages.flatMap((path) =>
    LOCALES.map((locale) => ({
      url: `${site}${path(locale)}`,
      alternates: { languages: Object.fromEntries(LOCALES.map((each) => [each, `${site}${path(each)}`])) },
    })),
  );
}
