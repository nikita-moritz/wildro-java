import type { MetadataRoute } from "next";

import { siteUrl } from "@/lib/env";
import { FILTER_PARAMS } from "@/lib/feed-filter";

/**
 * The same on every host. The dev host is kept out of the index by the
 * X-Robots-Tag header Caddy sets, and a crawler has to be allowed to fetch a
 * page to see that header; disallowing it here would hide the noindex.
 *
 * Filtered lists are closed to crawling rather than marked noindex (SPEC.md §0,
 * question 9): a crawler sees noindex only after spending its budget fetching
 * the page, and filters multiply into thousands of near-duplicates. Paging stays
 * open, since every page of the unfiltered feed is meant to be found.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        // The account pages are personal and say nothing to a crawler.
        disallow: ["/api/", "/*/sign-in", "/*/account", ...FILTER_PARAMS.flatMap((param) => [`/*?${param}=`, `/*&${param}=`])],
      },
    ],
    sitemap: `${siteUrl()}/sitemap.xml`,
  };
}
