import { ACTIVITY_TYPES, DIFFICULTIES, type ActivityType, type Difficulty } from "@/lib/vocabulary";

/**
 * The optional filters of the list and the map, as they travel in the address
 * bar. They are query parameters and never part of a path: filtered pages are
 * closed to crawlers (SPEC.md §0, question 9), and robots.ts disallows every
 * parameter below.
 */
export interface FeedFilter {
  types: ActivityType[];
  difficulties: Difficulty[];
  from: string | null;
  to: string | null;
  minKm: number | null;
  maxKm: number | null;
  near: { lat: number; lon: number; radiusKm: number } | null;
  /** Free or paid by the stated price; an event with no stated price matches neither. */
  price: PriceChoice | null;
  /** Text search over name, organiser and place in every language; the service folds it. */
  q: string | null;
  /**
   * The events of these groups only, by the groups' ids: "Meine Gruppen" (SPEC.md
   * §18.8). The browser knows which groups are the visitor's and puts their ids
   * here, so the list itself stays one cached render per address.
   */
  communities: number[];
}

export const PRICE_CHOICES = ["free", "paid"] as const;
export type PriceChoice = (typeof PRICE_CHOICES)[number];

export const EMPTY_FILTER: FeedFilter = {
  types: [],
  difficulties: [],
  from: null,
  to: null,
  minKm: null,
  maxKm: null,
  near: null,
  price: null,
  q: null,
  communities: [],
};

/** The query parameters a filter uses; robots.ts closes every one of them to crawlers. */
export const FILTER_PARAMS = ["type", "difficulty", "from", "to", "minKm", "maxKm", "lat", "lon", "radiusKm", "price", "q", "community"] as const;

/** The service's bound on the groups of one filter (FeedFilter.MAX_COMMUNITIES); more are dropped, not refused. */
export const MAX_COMMUNITIES = 30;

/** Longest search text sent on; the service drops anything past its own limit of the same length. */
export const MAX_QUERY_LENGTH = 100;

export const RADIUS_OPTIONS_KM = [10, 25, 50, 100] as const;

type Params = Record<string, string | string[] | undefined>;

const DAY = /^\d{4}-\d{2}-\d{2}$/;

function all(params: Params, key: string): string[] {
  const value = params[key];
  return value === undefined ? [] : Array.isArray(value) ? value : [value];
}

/**
 * A single-valued parameter. Next hands a repeated parameter over as an array,
 * and /api/points builds every parameter as one, so the first filled value
 * counts; ignoring arrays silently dropped the dates and distances on the map.
 */
function one(params: Params, key: string): string | null {
  return all(params, key).find((value) => value.trim() !== "") ?? null;
}

function number(value: string | null): number | null {
  if (value === null) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

/**
 * Reads what a visitor could have typed or tampered with. Anything unknown is
 * dropped rather than rejected: a filter is a convenience, and a broken one
 * should widen the list, not fail the page.
 */
export function parseFilter(params: Params): FeedFilter {
  const types = all(params, "type").filter((t): t is ActivityType => (ACTIVITY_TYPES as readonly string[]).includes(t));
  const difficulties = all(params, "difficulty").filter((d): d is Difficulty =>
    (DIFFICULTIES as readonly string[]).includes(d),
  );
  const from = one(params, "from");
  const to = one(params, "to");
  const lat = Number(one(params, "lat"));
  const lon = Number(one(params, "lon"));
  const radiusKm = Number(one(params, "radiusKm"));
  const near =
    Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180 &&
    (RADIUS_OPTIONS_KM as readonly number[]).includes(radiusKm) && one(params, "lat") !== null
      ? { lat: roundCoordinate(lat), lon: roundCoordinate(lon), radiusKm }
      : null;

  let minKm = number(one(params, "minKm"));
  let maxKm = number(one(params, "maxKm"));
  if (minKm !== null && maxKm !== null && maxKm < minKm) {
    [minKm, maxKm] = [maxKm, minKm];
  }

  const price = one(params, "price");
  const q = one(params, "q")?.trim().slice(0, MAX_QUERY_LENGTH) || null;
  // Sorted and without repeats, so the same groups make the same address and the same cache entry.
  const communities = [...new Set(all(params, "community").map(Number))]
    .filter((id) => Number.isSafeInteger(id) && id > 0)
    .sort((a, b) => a - b)
    .slice(0, MAX_COMMUNITIES);

  return {
    types: [...new Set(types)],
    difficulties: [...new Set(difficulties)],
    from: from && DAY.test(from) ? from : null,
    to: to && DAY.test(to) ? to : null,
    minKm,
    maxKm,
    near,
    price: (PRICE_CHOICES as readonly string[]).includes(price ?? "") ? (price as PriceChoice) : null,
    q,
    communities,
  };
}

/** A URL's query as parseFilter reads it, every parameter as the list of its values. */
export function paramsOf(search: URLSearchParams): Params {
  const params: Record<string, string[]> = {};
  search.forEach((value, key) => {
    params[key] = [...(params[key] ?? []), value];
  });
  return params;
}

export function isEmptyFilter(filter: FeedFilter): boolean {
  return (
    filter.types.length === 0 &&
    filter.difficulties.length === 0 &&
    filter.from === null &&
    filter.to === null &&
    filter.minKm === null &&
    filter.maxKm === null &&
    filter.near === null &&
    filter.price === null &&
    filter.q === null &&
    filter.communities.length === 0
  );
}

/** Written back into a query string, for the service and for links such as the next page. */
export function filterToParams(filter: FeedFilter): URLSearchParams {
  const params = new URLSearchParams();
  filter.types.forEach((t) => params.append("type", t));
  filter.difficulties.forEach((d) => params.append("difficulty", d));
  if (filter.from) params.set("from", filter.from);
  if (filter.to) params.set("to", filter.to);
  if (filter.minKm !== null) params.set("minKm", String(filter.minKm));
  if (filter.maxKm !== null) params.set("maxKm", String(filter.maxKm));
  if (filter.near) {
    params.set("lat", String(filter.near.lat));
    params.set("lon", String(filter.near.lon));
    params.set("radiusKm", String(filter.near.radiusKm));
  }
  if (filter.price) params.set("price", filter.price);
  if (filter.q) params.set("q", filter.q);
  filter.communities.forEach((id) => params.append("community", String(id)));
  return params;
}

/**
 * Two decimals, about a kilometre. Enough for a radius of ten or more, and the
 * visitor's exact position never lands in a server log or a cache key, which it
 * would as a query parameter. SPEC.md §0.4: the location serves one search and
 * is not kept.
 */
export function roundCoordinate(value: number): number {
  return Math.round(value * 100) / 100;
}
