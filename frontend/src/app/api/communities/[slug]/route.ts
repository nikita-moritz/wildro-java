import { callCommunityService } from "@/lib/communities";
import { communityAnswer } from "@/lib/community-answer";
import { currentSession, fromThisSite } from "@/lib/session";

/** PUT /api/communities/{slug} {community}: the editor's "save". */
export async function PUT(request: Request, { params }: RouteContext<"/api/communities/[slug]">): Promise<Response> {
  if (!fromThisSite(request)) {
    return Response.json({ code: "GENERIC" }, { status: 403 });
  }
  const { slug } = await params;
  const session = await currentSession();
  if (session.state !== "signed-in") {
    return Response.json({ code: session.state === "signed-out" ? "FORBIDDEN" : "UNAVAILABLE" }, { status: 401 });
  }
  const body = (await request.json().catch(() => null)) as { community?: unknown } | null;
  return communityAnswer(
    await callCommunityService("PUT", `/v1/communities/${encodeURIComponent(slug)}`, session.jwt, body?.community ?? {}),
  );
}
