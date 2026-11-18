import { standingIn } from "@/lib/communities";
import { currentSession } from "@/lib/session";

/**
 * GET /api/communities/{slug}/standing: the visitor's role, for the buttons on
 * the cached public page. Read in the browser after the page has loaded, so the
 * page itself stays one render for everyone.
 */
export async function GET(_request: Request, { params }: RouteContext<"/api/communities/[slug]/standing">) {
  const { slug } = await params;
  const session = await currentSession();
  if (session.state !== "signed-in") {
    return Response.json({ signedIn: false, role: null }, { headers: { "Cache-Control": "no-store" } });
  }
  const standing = await standingIn(session.jwt, slug);
  return Response.json({ signedIn: true, role: standing?.role ?? null }, { headers: { "Cache-Control": "no-store" } });
}
