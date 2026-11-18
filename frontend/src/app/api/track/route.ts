import { analyticsServiceUrl } from "@/lib/env";
import { clientAddress } from "@/lib/client-address";

/**
 * One page view, sent by the tracker in the layout with sendBeacon. The
 * browser cannot reach the analytics service, which listens on loopback, so
 * this route forwards on its behalf, adding what only the server knows: the
 * host the visitor asked for, their address and their user agent. The service
 * decides whether the view counts (SPEC.md §13: wildro.app only, no bots, at
 * most 60 a minute per address) and stores neither the address nor the agent.
 *
 * Always 204. The beacon discards the answer and has no user to tell, and a
 * slow or absent analytics service must never slow the page down, so the
 * forward is bounded by a short timeout and its outcome ignored.
 */
const OPT_OUT = /(?:^|;\s*)wildro_noanalytics=/;

const MAX_BODY = 4096;

export async function POST(request: Request): Promise<Response> {
  const done = new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
  const service = analyticsServiceUrl();
  if (!service || OPT_OUT.test(request.headers.get("cookie") ?? "")) {
    return done;
  }

  const raw = await request.text().catch(() => "");
  if (!raw || raw.length > MAX_BODY) {
    return done;
  }
  let view: Record<string, unknown>;
  try {
    view = JSON.parse(raw);
  } catch {
    return done;
  }
  if (typeof view !== "object" || view === null) {
    return done;
  }

  const pick = (key: string) => (typeof view[key] === "string" ? (view[key] as string) : null);
  const body = JSON.stringify({
    path: pick("path"),
    referrer: pick("referrer"),
    utmSource: pick("utmSource"),
    utmMedium: pick("utmMedium"),
    utmCampaign: pick("utmCampaign"),
    host: request.headers.get("host"),
    clientIp: clientAddress(request),
    userAgent: request.headers.get("user-agent"),
  });

  await fetch(new URL("/v1/track", service), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
    signal: AbortSignal.timeout(1500),
  }).catch(() => undefined);
  return done;
}

