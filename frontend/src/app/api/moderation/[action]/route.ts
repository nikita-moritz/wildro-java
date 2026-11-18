import { callEventService, publicChanged } from "@/lib/own-events";
import { routes } from "@/lib/routes";
import { localeOf, seeOther, text } from "@/lib/route-helpers";
import { currentSession, fromThisSite } from "@/lib/session";

/**
 * POST /api/moderation/{suspend|reinstate|dismiss}, forms on the moderation
 * page. The role is checked by the event service, not here.
 */
export async function POST(request: Request, { params }: RouteContext<"/api/moderation/[action]">) {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const { action } = await params;
  const form = await request.formData().catch(() => null);
  const locale = localeOf(form);
  const back = routes.moderation(locale);
  const session = await currentSession();
  if (session.state === "signed-out") {
    return seeOther(routes.signIn(locale));
  }
  if (session.state === "unavailable") {
    return seeOther(`${back}?error=UNAVAILABLE`);
  }
  const slug = encodeURIComponent(text(form, "slug"));
  let response: Response | null = null;
  if (action === "suspend") {
    response = await callEventService("POST", `/v1/moderation/events/${slug}/suspend`, session.jwt, {
      reason: text(form, "reason"),
    });
  } else if (action === "reinstate") {
    response = await callEventService("POST", `/v1/moderation/events/${slug}/reinstate`, session.jwt);
  } else if (action === "dismiss") {
    response = await callEventService("POST", `/v1/moderation/reports/${encodeURIComponent(text(form, "report"))}/dismiss`, session.jwt);
  }
  if (!response?.ok) {
    return seeOther(`${back}?error=${response ? "WRONG_STATE" : "UNAVAILABLE"}`);
  }
  if (action !== "dismiss") {
    publicChanged(decodeURIComponent(slug));
  }
  return seeOther(`${back}?done=1`);
}
