import { callCommunityService } from "@/lib/communities";
import { communityAnswer } from "@/lib/community-answer";
import { currentSession, fromThisSite } from "@/lib/session";

/** POST /api/communities {community}: the editor's "create". */
export async function POST(request: Request): Promise<Response> {
  if (!fromThisSite(request)) {
    return Response.json({ code: "GENERIC" }, { status: 403 });
  }
  const session = await currentSession();
  if (session.state !== "signed-in") {
    return Response.json({ code: session.state === "signed-out" ? "FORBIDDEN" : "UNAVAILABLE" }, { status: 401 });
  }
  const body = (await request.json().catch(() => null)) as { community?: unknown } | null;
  return communityAnswer(await callCommunityService("POST", "/v1/communities", session.jwt, body?.community ?? {}));
}
