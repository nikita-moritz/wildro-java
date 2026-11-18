import { routes } from "@/lib/routes";
import { localeOf, seeOther, text } from "@/lib/route-helpers";
import { callUserService, currentSession, fromThisSite } from "@/lib/session";

/**
 * POST /api/account/terms, the account page's acceptance form. The version is
 * the one the page showed; the user service refuses any other, so consent is
 * recorded only to the text the person had in front of them.
 */
export async function POST(request: Request): Promise<Response> {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const form = await request.formData().catch(() => null);
  const locale = localeOf(form);
  const session = await currentSession();
  if (session.state === "signed-out") {
    return seeOther(routes.signIn(locale));
  }
  if (session.state === "unavailable") {
    return seeOther(`${routes.account(locale)}?error=failed`);
  }
  const response = await callUserService("POST", "/v1/me/terms", session.jwt, { version: text(form, "version") });
  return seeOther(`${routes.account(locale)}?${response?.ok ? "terms=1" : "error=failed"}`);
}
