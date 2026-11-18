import { clientAddress } from "@/lib/client-address";
import { callEventService } from "@/lib/own-events";

/**
 * POST /api/reports {slug, reason, details}, open to guests (SPEC.md §18.2,
 * DSA Art. 16). The visitor's address goes to the service for its rate limit
 * only; neither side stores it.
 */
export async function POST(request: Request): Promise<Response> {
  const body = await request.json().catch(() => null);
  const address = clientAddress(request);
  const response = await callEventService("POST", "/v1/reports", null, body ?? {}, address ? { "X-Client-Address": address } : {});
  if (!response) {
    return Response.json({ ok: false }, { status: 503 });
  }
  return Response.json({ ok: response.ok }, { status: response.status === 429 ? 429 : response.ok ? 202 : 400 });
}
