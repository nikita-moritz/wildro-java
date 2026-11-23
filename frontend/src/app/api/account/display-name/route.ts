import { localeOf, nextPath, seeOther } from "@/lib/route-helpers";
import { routes } from "@/lib/routes";
import { callUserService, currentSession, fromThisSite } from "@/lib/session";

/**
 * POST /api/account/display-name, a plain form post from the account page, so
 * renaming works without JavaScript. Answers with a 303 back to the page,
 * carrying the outcome in the address.
 */
export async function POST(request: Request): Promise<Response> {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const form = await request.formData().catch(() => null);
  const locale = localeOf(form);
  const back = (outcome: string) => seeOther(`${routes.account(locale)}?${outcome}`);

  const session = await currentSession();
  if (session.state === "signed-out") {
    return seeOther(routes.signIn(locale));
  }
  if (session.state === "unavailable") {
    return back("error=failed");
  }
  const name = form?.get("displayName");
  const response = await callUserService("PUT", "/v1/me/display-name", session.jwt, {
    displayName: typeof name === "string" ? name : "",
  });
  if (response?.status === 422) {
    return back("error=name");
  }
  // Named from a form that needed the name (a new event, a new group): straight back there.
  const next = nextPath(form, locale);
  if (response?.ok && next) {
    return seeOther(next);
  }
  return back(response?.ok ? "saved=1" : "error=failed");
}
