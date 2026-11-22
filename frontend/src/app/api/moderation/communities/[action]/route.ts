import { callCommunityService, communityChanged } from "@/lib/communities";
import { communityCode } from "@/lib/community-answer";
import { routes } from "@/lib/routes";
import { localeOf, seeOther, text } from "@/lib/route-helpers";
import { currentSession, fromThisSite } from "@/lib/session";

/**
 * POST /api/moderation/communities/{suspend|reinstate|transfer|dismiss}, forms on the
 * community moderation page. The roles are the community service's to check:
 * taking down and putting back for moderators, handing over for superusers.
 */
export async function POST(request: Request, { params }: RouteContext<"/api/moderation/communities/[action]">) {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const { action } = await params;
  const form = await request.formData().catch(() => null);
  const locale = localeOf(form);
  const slug = text(form, "slug");
  const q = text(form, "q");
  // Back to the same search, with the outcome.
  const back = (outcome: "done" | "error", value: string) => {
    const query = new URLSearchParams(q ? { q } : {});
    query.set(outcome, value);
    return `${routes.moderationCommunities(locale)}?${query}`;
  };
  const session = await currentSession();
  if (session.state === "signed-out") {
    return seeOther(routes.signIn(locale));
  }
  if (session.state === "unavailable") {
    return seeOther(back("error", "UNAVAILABLE"));
  }
  const path = `/v1/moderation/communities/${encodeURIComponent(slug)}`;
  const calls: Record<string, () => Promise<Response | null>> = {
    suspend: () => callCommunityService("POST", `${path}/suspend`, session.jwt, { reason: text(form, "reason") }),
    reinstate: () => callCommunityService("POST", `${path}/reinstate`, session.jwt),
    transfer: () => callCommunityService("POST", `${path}/transfer`, session.jwt, { userId: text(form, "user") }),
    dismiss: () =>
      callCommunityService("POST", `/v1/moderation/reports/${encodeURIComponent(text(form, "report"))}/dismiss`, session.jwt),
  };
  const call = calls[action];
  if (!call || !slug) {
    return seeOther(back("error", "GENERIC"));
  }
  const response = await call();
  if (!response?.ok) {
    const json = await response?.json().catch(() => null);
    // Spring's own refusals (validation, a missing role) carry no code.
    const code = !response
      ? "UNAVAILABLE"
      : response.status === 400
        ? "INVALID"
        : response.status === 403
          ? "FORBIDDEN"
          : communityCode(json?.code);
    return seeOther(back("error", code));
  }
  // Taking down or putting back changes the public page and the list; a new
  // owner or a dismissed report changes neither.
  if (action === "suspend" || action === "reinstate") {
    communityChanged(slug);
  }
  return seeOther(back("done", "1"));
}
