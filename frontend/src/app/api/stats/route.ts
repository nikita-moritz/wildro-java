import { timingSafeEqual } from "node:crypto";

import { analyticsServiceUrl, statsApiKey } from "@/lib/env";

/**
 * GET /api/stats?period=day|week|month|all, read by the owner dashboard on
 * nikone.dev with its key in X-Stats-Key. The aggregates come from the
 * analytics service unchanged: it already answers the shared contract, where
 * anything not measured is null rather than zero.
 */
const PERIODS = new Set(["day", "week", "month", "all"]);

function sameKey(presented: string, expected: string): boolean {
  const a = Buffer.from(presented);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

export async function GET(request: Request): Promise<Response> {
  const key = statsApiKey();
  const service = analyticsServiceUrl();
  if (!key || !service) {
    return Response.json({ error: "stats_not_configured" }, { status: 503 });
  }
  if (!sameKey(request.headers.get("x-stats-key") ?? "", key)) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }

  const requested = new URL(request.url).searchParams.get("period") ?? "month";
  const url = new URL("/v1/stats", service);
  url.searchParams.set("period", PERIODS.has(requested) ? requested : "month");

  const response = await fetch(url, { cache: "no-store", signal: AbortSignal.timeout(5000) }).catch(() => null);
  if (!response?.ok) {
    return Response.json({ error: "the analytics service did not answer" }, { status: 502 });
  }
  return new Response(await response.text(), {
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}
