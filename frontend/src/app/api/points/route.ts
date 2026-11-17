import { z } from "zod";

import { eventServiceUrl } from "@/lib/env";
import { filterToParams, paramsOf, parseFilter } from "@/lib/feed-filter";
import { pointsPageSchema } from "@/lib/events-api";
import { LOCALES } from "@/lib/locales";

/**
 * The map's markers for one viewport. The browser cannot reach the event
 * service, which listens on loopback only, so this route asks on its behalf.
 * Read-only, and nothing is stored: the viewport and the filters are the whole
 * request.
 *
 * Not cached: every pan is a different rectangle, and caching them would fill
 * memory with viewports nobody asks for twice.
 */
const viewportSchema = z
  .object({
    locale: z.enum(LOCALES),
    south: z.coerce.number().min(-90).max(90),
    west: z.coerce.number().min(-180).max(180),
    north: z.coerce.number().min(-90).max(90),
    east: z.coerce.number().min(-180).max(180),
  })
  .refine((v) => v.north > v.south && v.east > v.west, "north above south and east right of west");

export async function GET(request: Request): Promise<Response> {
  const query = new URL(request.url).searchParams;
  const viewport = viewportSchema.safeParse(Object.fromEntries(query));
  if (!viewport.success) {
    return Response.json({ error: "expected locale, south, west, north and east" }, { status: 400 });
  }

  const filter = parseFilter(paramsOf(query));

  const url = new URL("/v1/events/points", eventServiceUrl());
  // The map ignores "near me": the viewport already says where to look.
  filterToParams({ ...filter, near: null }).forEach((value, key) => url.searchParams.append(key, value));
  for (const [key, value] of Object.entries(viewport.data)) {
    url.searchParams.set(key, String(value));
  }

  const response = await fetch(url, { cache: "no-store" });
  if (!response.ok) {
    return Response.json({ error: "the event service did not answer" }, { status: 502 });
  }
  const points = pointsPageSchema.parse(await response.json());
  return Response.json(points, { headers: { "Cache-Control": "no-store" } });
}
