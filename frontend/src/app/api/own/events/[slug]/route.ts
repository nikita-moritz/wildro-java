import { answer, callEventService } from "@/lib/own-events";
import { currentSession, fromThisSite } from "@/lib/session";

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
