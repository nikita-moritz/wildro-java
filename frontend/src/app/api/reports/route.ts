import { clientAddress } from "@/lib/client-address";
import { callCommunityService } from "@/lib/communities";
import { callEventService } from "@/lib/own-events";

/**
 * POST /api/reports {target, slug, reason, details}, open to guests (SPEC.md
 * §18.2, DSA Art. 16). target is "event" (the default) or "community"; each
 * goes to the service that owns it. The visitor's address goes along for the
 * rate limit only; no side stores it.
 */
export async function POST(request: Request): Promise<Response> {
  const body = await request.json().catch(() => null);
  const address = clientAddress(request);
  const headers: Record<string, string> = address ? { "X-Client-Address": address } : {};
  const slug = typeof body?.slug === "string" ? body.slug : "";
  const response =
    body?.target === "community"
      ? await callCommunityService(
          "POST",
          `/v1/communities/${encodeURIComponent(slug)}/reports`,
          null,
          { reason: body?.reason, details: body?.details },
          headers,
        )
      : await callEventService("POST", "/v1/reports", null, body ?? {}, headers);
  if (!response) {
    return Response.json({ ok: false }, { status: 503 });
  }
  return Response.json({ ok: response.ok }, { status: response.status === 429 ? 429 : response.ok ? 202 : 400 });
}
