import { NextResponse } from "next/server";

import { fromThisSite, sessionCookie } from "@/lib/session";

/**
 * POST /api/auth/logout: deletes the session cookie. Called after the SDK has
 * ended the session at elvix; script cannot delete an httpOnly cookie, so this
 * is the only way it goes before its time. 204, no body.
 */
export async function POST(request: Request): Promise<Response> {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const response = new NextResponse(null, { status: 204 });
  response.cookies.set(sessionCookie("", 0));
  return response;
}
