import { NextResponse } from "next/server";
import { z } from "zod";

import { exchangeForJwt, fromThisSite, SESSION_MAX_AGE, sessionCookie } from "@/lib/session";

/**
 * POST /api/auth/session {token}: the sign-in form hands over the session token
 * elvix gave it, and this moves it into an httpOnly cookie. The token is checked
 * at elvix before it is kept, so a cookie only ever holds a live session of our
 * own application.
 */
const body = z.object({ token: z.string().min(16).max(4096) });

export async function POST(request: Request): Promise<Response> {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const parsed = body.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json({ error: "bad_request" }, { status: 400 });
  }
  if (!(await exchangeForJwt(parsed.data.token))) {
    return Response.json({ error: "invalid_session" }, { status: 401 });
  }
  const response = new NextResponse(null, { status: 204 });
  response.cookies.set(sessionCookie(parsed.data.token, SESSION_MAX_AGE));
  return response;
}
