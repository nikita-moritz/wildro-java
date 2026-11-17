import { MAX_FEED_PAGE } from "@/lib/events-api";

/**
 * The feed's ?page= parameter, one-based as in the address bar. Anything that
 * is not a single whole number in range is not a page: the caller answers 404
 * rather than inventing one, because every accepted value is an address a
 * crawler may index.
 */
export function parseFeedPage(raw: string | string[] | undefined): number | null {
  if (raw === undefined) {
    return 1;
  }
  if (Array.isArray(raw) || !/^[1-9][0-9]*$/.test(raw)) {
    return null;
  }
  const page = Number(raw);
  return page <= MAX_FEED_PAGE ? page : null;
}
