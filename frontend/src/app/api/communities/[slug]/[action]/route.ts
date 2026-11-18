import { callCommunityService, communityChanged } from "@/lib/communities";
import { communityCode } from "@/lib/community-answer";
import { routes } from "@/lib/routes";
import { localeOf, seeOther, text } from "@/lib/route-helpers";
import { currentSession, fromThisSite } from "@/lib/session";

/**
 * POST /api/communities/{slug}/{join|leave|close|transfer|role|remove}, plain
 * forms on the community and management pages. A 303 back, carrying the
 * outcome in the address. Every rule is the community service's.
 */
export async function POST(request: Request, { params }: RouteContext<"/api/communities/[slug]/[action]">) {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const { slug, action } = await params;
  const form = await request.formData().catch(() => null);
  const locale = localeOf(form);
  const publicPage = action === "join" || action === "leave";
  const back = publicPage ? routes.community(locale, slug) : routes.manageCommunity(locale, slug);
  const session = await currentSession();
  if (session.state === "signed-out") {
    return seeOther(routes.signIn(locale));
  }
  if (session.state === "unavailable") {
    return seeOther(`${back}?error=UNAVAILABLE`);
  }
  const path = `/v1/communities/${encodeURIComponent(slug)}`;
  const user = encodeURIComponent(text(form, "user"));
  const calls: Record<string, () => Promise<Response | null>> = {
    join: () => callCommunityService("POST", `${path}/join`, session.jwt),
    leave: () => callCommunityService("POST", `${path}/leave`, session.jwt),
    close: () => callCommunityService("POST", `${path}/close`, session.jwt),
    transfer: () => callCommunityService("POST", `${path}/transfer`, session.jwt, { userId: text(form, "user") }),
    role: () => callCommunityService("POST", `${path}/members/${user}/role`, session.jwt, { role: text(form, "role") }),
    remove: () => callCommunityService("POST", `${path}/members/${user}/remove`, session.jwt),
  };
  const call = calls[action];
  if (!call) {
    return seeOther(back);
  }
  const response = await call();
  if (!response?.ok) {
    const json = await response?.json().catch(() => null);
    return seeOther(`${back}?error=${response ? communityCode(json?.code) : "UNAVAILABLE"}`);
  }
  // A close cancels the community's events, which also changes the feed.
  communityChanged(slug, action === "close");
  return seeOther(`${back}?done=1`);
}
