import "server-only";

import { z } from "zod";

/**
 * Configuration, checked at the boundary and read lazily: parsing at module
 * load would crash `next build` on a machine that only builds, and a mistyped
 * URL should fail on first use with a message rather than as a fetch to nowhere.
 *
 * One getter per variable, so a page that only needs the site origin (every
 * page's canonical link) does not demand the event service's address at build
 * time, when nothing talks to the service at all.
 */

const url = z.url().transform((value) => value.replace(/\/+$/, ""));

let site: string | undefined;
let service: string | undefined;
let analytics: string | null | undefined;

/** The public origin, for canonical and alternate links. */
export function siteUrl(): string {
  site ??= url.parse(process.env.SITE_URL, { error: () => "SITE_URL must be an absolute URL" });
  return site;
}

/**
 * The hosts that show the real face. java.wildro.app is this implementation's
 * production, the one a CV links to, so it is branded like wildro.app.
 */
const PRODUCTION_HOSTS = new Set(["wildro.app", "java.wildro.app"]);

/**
 * Which face the site shows: "wildro" on a production host, "demo.w" with grey
 * icons everywhere else (a local run, a preview), so an installed demo is never
 * mistaken for the real app on a home screen. Read from SITE_URL, which every
 * build and run has.
 */
export function brand(): { name: string; icons: string } {
  return PRODUCTION_HOSTS.has(new URL(siteUrl()).hostname)
    ? { name: "wildro", icons: "/icons" }
    : { name: "demo.w", icons: "/icons/demo" };
}

/** The event service, reached from this server only and never exposed to the browser. */
export function eventServiceUrl(): string {
  service ??= url.parse(process.env.EVENT_SERVICE_URL, {
    error: () => "EVENT_SERVICE_URL must be an absolute URL",
  });
  return service;
}

/**
 * The shared secret the event service presents to trigger revalidation. Absent
 * or too short means the endpoint refuses every call rather than accepting any:
 * fail closed.
 */
export function revalidateSecret(): string | undefined {
  const secret = process.env.REVALIDATE_SECRET;
  return secret && secret.length >= 32 ? secret : undefined;
}

/**
 * Mapbox's public access token. It reaches the browser by design, which is why
 * it must be a "pk." token restricted to this site's addresses in the Mapbox
 * account, never a secret "sk." one. Absent means the map page says the map is
 * not available rather than rendering an empty frame.
 */
export function mapboxToken(): string | undefined {
  const token = process.env.MAPBOX_ACCESS_TOKEN;
  return token && token.startsWith("pk.") ? token : undefined;
}

/**
 * The analytics service, reached from this server only. Optional: without it
 * the site works exactly as before and page views are simply not counted, which
 * is the right failure for a measurement (SPEC.md §13).
 */
export function analyticsServiceUrl(): string | null {
  if (analytics === undefined) {
    const parsed = url.safeParse(process.env.ANALYTICS_SERVICE_URL);
    analytics = parsed.success ? parsed.data : null;
  }
  return analytics;
}

/**
 * The key the nikone.dev owner dashboard presents to /api/stats. Absent or too
 * short means the endpoint refuses every call: fail closed.
 */
export function statsApiKey(): string | undefined {
  const key = process.env.STATS_API_KEY;
  return key && key.length >= 32 ? key : undefined;
}

let users: string | null | undefined;

/**
 * The user service, reached from this server only. Optional like analytics:
 * without it the site reads exactly as before and only the account pages say
 * that signing in is not available.
 */
export function userServiceUrl(): string | null {
  if (users === undefined) {
    const parsed = url.safeParse(process.env.USER_SERVICE_URL);
    users = parsed.success ? parsed.data : null;
  }
  return users;
}

/**
 * The elvix application's public clientId. Not a secret: it reaches the
 * browser in the sign-in form. It is also the audience every token must carry,
 * which is how this server tells our tokens from another elvix app's.
 */
export function elvixClientId(): string | undefined {
  const id = process.env.ELVIX_CLIENT_ID;
  return id && id.startsWith("elvix_pub_") ? id : undefined;
}
