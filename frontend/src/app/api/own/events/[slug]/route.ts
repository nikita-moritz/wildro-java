import { answer, callEventService, getMine } from "@/lib/own-events";
import { currentSession, fromThisSite } from "@/lib/session";

/**
 * GET /api/own/events/{slug}: whether the visitor may edit this event, for the
 * "Edit" button on the cached public page. Asked from the browser after the page
 * has loaded, so the page itself stays one render for everyone.
 */
export async function GET(_request: Request, { params }: RouteContext<"/api/own/events/[slug]">): Promise<Response> {
  const { slug } = await params;
  const session = await currentSession();
  const event = session.state === "signed-in" ? await getMine(session.jwt, slug) : null;
  const editable = event?.status === "DRAFT" || event?.status === "PUBLISHED";
  return Response.json({ editable }, { headers: { "Cache-Control": "no-store" } });
}

/** PUT /api/own/events/{slug} {event}: the editor's "save" on an existing event. */
export async function PUT(request: Request, { params }: RouteContext<"/api/own/events/[slug]">): Promise<Response> {
  if (!fromThisSite(request)) {
    return Response.json({ code: "GENERIC" }, { status: 403 });
  }
  const { slug } = await params;
  const session = await currentSession();
  if (session.state !== "signed-in") {
    return Response.json({ code: session.state === "signed-out" ? "NOT_YOURS" : "UNAVAILABLE" }, { status: 401 });
  }
  const body = (await request.json().catch(() => null)) as { event?: unknown } | null;
  return answer(await callEventService("PUT", `/v1/own/events/${encodeURIComponent(slug)}`, session.jwt, body?.event ?? {}));
}
