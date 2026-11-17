import type { NextConfig } from "next";
// Next's own default list, extended rather than copied: if this internal path
// moves in an upgrade the build fails loudly instead of keeping a stale copy.
import { HTML_LIMITED_BOT_UA_RE } from "next/dist/shared/lib/router/utils/html-bots";

const nextConfig: NextConfig = {
  /**
   * A self-contained server.js with only the files it needs, so a release is one
   * directory, deployed like the service's jar: build once, copy, swap a symlink.
   */
  output: "standalone",

  /**
   * The current caching model: "use cache" with cacheTag and cacheLife. It maps
   * one to one onto the decided read path, a cached render busted by a change
   * event from the service, with invalidation by tag as one contract.
   */
  cacheComponents: true,

  /**
   * Crawlers get a fully blocking render: a real 404 status for an address that
   * does not exist, instead of a not-found page served with 200 after the shell
   * has already gone out. Next's default list covers Bing, DuckDuckGo, Yandex,
   * Baidu, Apple and the social previews but leaves out plain Googlebot, on the
   * grounds that it runs JavaScript. This product lives on Google, so Googlebot
   * is added, with the AI crawlers that fetch documents the same way.
   *
   * Never /.*\/ for everyone: that was tried and made every browser prefetch of
   * an event page render blocking, waiting on route params a prefetch never
   * resolves, so each one held a stream open for good. Crawlers request
   * documents and never prefetch.
   */
  htmlLimitedBots: new RegExp(
    `${HTML_LIMITED_BOT_UA_RE.source}|Googlebot|GPTBot|OAI-SearchBot|ClaudeBot|Claude-SearchBot|PerplexityBot|CCBot|Amazonbot`,
    "i",
  ),

  poweredByHeader: false,

  async redirects() {
    // German is the x-default. A fixed redirect, not a guess from Accept-Language
    // or IP: SPEC.md §0.4 forbids automatic redirects, because a crawler arriving
    // from elsewhere would be steered away from the other language versions.
    return [
      { source: "/", destination: "/de", permanent: true },
      // Addresses of the working version that wildro.app answered until the
      // cutover (2026-10-20). Search engines and old links still ask for them;
      // each goes to the nearest page here rather than to a 404. German,
      // because the working version had no language in its addresses.
      { source: "/explore", has: [{ type: "query", key: "tab", value: "list" }], destination: "/de/events", permanent: true },
      { source: "/explore", destination: "/de/map", permanent: true },
      { source: "/events", destination: "/de/events", permanent: true },
      // Its events were numbered and none of them is still ahead; there is no page to map them to.
      { source: "/events/:id(\\d+)", destination: "/de/events", permanent: true },
      { source: "/impressum", destination: "/de/legal-notice", permanent: true },
      { source: "/datenschutz", destination: "/de/privacy", permanent: true },
      // Accounts, communities and terms are not part of stage one. Temporary,
      // because some of them may come back under these addresses.
      { source: "/nutzungsbedingungen", destination: "/de", permanent: false },
      { source: "/communities/:path*", destination: "/de", permanent: false },
      { source: "/auth/:path*", destination: "/de", permanent: false },
      { source: "/profile/:path*", destination: "/de", permanent: false },
      { source: "/admin/:path*", destination: "/de", permanent: false },
    ];
  },

  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Frame-Options", value: "DENY" },
          // "Near me" will ask for the location on this origin, after a click.
          { key: "Permissions-Policy", value: "geolocation=(self), camera=(), microphone=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
