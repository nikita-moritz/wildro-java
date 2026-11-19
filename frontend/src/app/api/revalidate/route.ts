import { revalidateTag } from "next/cache";
import { timingSafeEqual } from "node:crypto";
import { z } from "zod";

import { revalidateSecret } from "@/lib/env";

/**
 * Called by the event and community services after a write, with the tags it affects.
 * This is how a page changes when its data does, instead of waiting out a
 * timer (AGENTS.md: cache invalidation by tag is one contract).
 *
 * Only these shapes are accepted, so a caller holding the secret still cannot
 * flush arbitrary cache entries: the feed, the map, one event page, the list of
 * groups, one group's page. The community service calls it too, after an
 * ownership change or a close it carried out on its own.
 */
const TAG = /^(events:feed|events:map|event:[a-z0-9]+(-[a-z0-9]+)*|communities:list|community:[a-z0-9]+(-[a-z0-9]+)*)$/;

const bodySchema = z.object({
  tags: z.array(z.string().regex(TAG)).min(1).max(50),
});

function sameSecret(presented: string, expected: string): boolean {
  const a = Buffer.from(presented);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

export async function POST(request: Request): Promise<Response> {
  const secret = revalidateSecret();
  if (!secret) {
    return Response.json({ error: "revalidation is not configured" }, { status: 503 });
  }

  const authorization = request.headers.get("authorization") ?? "";
  const presented = authorization.startsWith("Bearer ") ? authorization.slice("Bearer ".length) : "";
  if (!sameSecret(presented, secret)) {
    return Response.json({ error: "unauthorised" }, { status: 401 });
  }

  const parsed = bodySchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json({ error: "expected { tags: [...] } with known tag shapes" }, { status: 400 });
  }

  // expire: 0 rather than "max": the call comes from another service, where
  // updateTag is unavailable, and an admin edit should not be served stale.
  for (const tag of parsed.data.tags) {
    revalidateTag(tag, { expire: 0 });
  }
  return Response.json({ revalidated: parsed.data.tags });
}
