import { answer, callEventService } from "@/lib/own-events";
import { currentSession, fromThisSite } from "@/lib/session";

/**
 * POST /api/own/events {event, publish}: the editor's "save draft" and
 * "publish". Answers {slug} on success, {code, field?} on refusal, so the form
 * keeps what was typed and says what to fix.
 */
export async function POST(request: Request): Promise<Response> {
  if (!fromThisSite(request)) {
    return Response.json({ code: "GENERIC" }, { status: 403 });
  }
  const session = await currentSession();
  if (session.state !== "signed-in") {
    return Response.json({ code: session.state === "signed-out" ? "NOT_YOURS" : "UNAVAILABLE" }, { status: 401 });
  }
  const body = (await request.json().catch(() => null)) as { event?: unknown; publish?: unknown } | null;
  const publish = body?.publish === true;
  const response = await callEventService("POST", `/v1/own/events?publish=${publish}`, session.jwt, body?.event ?? {});
  return answer(response);
}
