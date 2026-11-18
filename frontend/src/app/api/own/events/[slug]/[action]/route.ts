import { callEventService, errorCode, publicChanged } from "@/lib/own-events";
import { routes } from "@/lib/routes";
import { localeOf, seeOther } from "@/lib/route-helpers";
import { currentSession, fromThisSite } from "@/lib/session";

/**
 * POST /api/own/events/{slug}/publish and .../cancel, plain forms on the edit
 * page. A 303 back to the page, carrying the outcome in the address.
 */
const ACTIONS = new Set(["publish", "cancel"]);

export async function POST(request: Request, { params }: RouteContext<"/api/own/events/[slug]/[action]">) {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const { slug, action } = await params;
  const form = await request.formData().catch(() => null);
  const locale = localeOf(form);
  if (!ACTIONS.has(action)) {
    return seeOther(routes.myEvents(locale));
  }
  const session = await currentSession();
  if (session.state === "signed-out") {
    return seeOther(routes.signIn(locale));
  }
  const page = routes.editEvent(locale, slug);
  if (session.state === "unavailable") {
    return seeOther(`${page}?error=UNAVAILABLE`);
  }
  const response = await callEventService("POST", `/v1/own/events/${encodeURIComponent(slug)}/${action}`, session.jwt);
  if (!response?.ok) {
    const json = await response?.json().catch(() => null);
    return seeOther(`${page}?error=${response ? errorCode(json?.code) : "UNAVAILABLE"}`);
  }
  publicChanged(slug);
  return seeOther(`${page}?done=${action}`);
}
