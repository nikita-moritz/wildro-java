import { userServiceUrl } from "@/lib/env";

/**
 * POST /api/webhooks/elvix: elvix's deliveries, passed to the user service as
 * they came. The body is forwarded as bytes, never parsed: the signature covers
 * the exact body, and the service is what checks it. The status goes back to
 * elvix unchanged, so a failure there is retried.
 */
export async function POST(request: Request): Promise<Response> {
  const service = userServiceUrl();
  if (!service) {
    return new Response(null, { status: 503 });
  }
  const response = await fetch(new URL("/v1/webhooks/elvix", service), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Elvix-Signature": request.headers.get("elvix-signature") ?? "",
      "Elvix-Event-Id": request.headers.get("elvix-event-id") ?? "",
    },
    body: await request.arrayBuffer(),
    cache: "no-store",
    signal: AbortSignal.timeout(10000),
  }).catch(() => null);
  return new Response(null, { status: response?.status ?? 502 });
}
