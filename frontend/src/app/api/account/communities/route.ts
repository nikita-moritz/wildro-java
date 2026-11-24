import { myCommunities } from "@/lib/communities";
import { currentSession } from "@/lib/session";

/**
 * GET /api/account/communities: the ids of the open groups the visitor belongs
 * to, for the filter "Meine Gruppen" (SPEC.md §18.8). Asked by the browser, so
 * the list and the map stay one cached render for everyone. A guest, or a
 * failure to ask, gets an empty list: the filter then simply does not show.
 */
export async function GET(): Promise<Response> {
  const session = await currentSession();
  const standings = session.state === "signed-in" ? await myCommunities(session.jwt) : null;
  const ids = (standings ?? []).filter((each) => each.status === "ACTIVE").map((each) => each.id);
  return Response.json({ ids }, { headers: { "Cache-Control": "no-store" } });
}
