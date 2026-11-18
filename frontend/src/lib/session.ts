import "server-only";

import { cookies } from "next/headers";
import { z } from "zod";

import { elvixClientId, siteUrl, userServiceUrl } from "@/lib/env";

/**
 * The signed-in person, as this server sees them.
 *
 * The browser holds elvix's opaque session token in an httpOnly cookie that
 * script on the page cannot read. For each request that needs the person, this
 * server trades it for a short-lived JWT at elvix and sends that to the user
 * service, which verifies it locally. No service ever sees the cookie, and no
 * page that does not need the person reads it: public pages stay cached.
 */

export const SESSION_COOKIE = "wildro_session";

const ELVIX = "https://elvix.is";

/** Thirty days, elvix's own session length; elvix ends it sooner if it must. */
export const SESSION_MAX_AGE = 60 * 60 * 24 * 30;

const tokenAnswer = z.object({
  ok: z.literal(true),
  access_token: z.string(),
  audience: z.string(),
});

/**
 * Trades the session token for a JWT. Null when elvix refuses it (ended,
 * revoked, blocked) or when it belongs to another elvix application: the
 * audience is our clientId or the token is not ours.
 */
export async function exchangeForJwt(sessionToken: string): Promise<string | null> {
  const clientId = elvixClientId();
  if (!clientId) {
    return null;
  }
  const response = await fetch(`${ELVIX}/api/v1/token`, {
    method: "POST",
    headers: { Authorization: `Bearer ${sessionToken}`, "x-elvix-client-id": clientId },
    cache: "no-store",
    signal: AbortSignal.timeout(5000),
  }).catch(() => null);
  if (!response?.ok) {
    return null;
  }
  const parsed = tokenAnswer.safeParse(await response.json().catch(() => null));
  return parsed.success && parsed.data.audience === clientId ? parsed.data.access_token : null;
}

const me = z.object({
  id: z.string(),
  email: z.string().nullable(),
  displayName: z.string().nullable(),
  roles: z.array(z.string()),
  terms: z.object({
    current: z.string().nullable(),
    accepted: z.string().nullable(),
    acceptedAt: z.string().nullable(),
    upToDate: z.boolean(),
  }),
});

export type Me = z.infer<typeof me>;

/** What reading the person can come to. "unavailable" is ours or elvix's fault, never theirs. */
export type Session = { state: "signed-in"; me: Me; jwt: string } | { state: "signed-out" } | { state: "unavailable" };

export async function currentSession(): Promise<Session> {
  const token = (await cookies()).get(SESSION_COOKIE)?.value;
  if (!token) {
    return { state: "signed-out" };
  }
  const service = userServiceUrl();
  if (!service || !elvixClientId()) {
    return { state: "unavailable" };
  }
  const jwt = await exchangeForJwt(token);
  if (!jwt) {
    return { state: "signed-out" };
  }
  const response = await callUserService("GET", "/v1/me", jwt);
  if (response?.status === 401) {
    return { state: "signed-out" };
  }
  const parsed = me.safeParse(response?.ok ? await response.json().catch(() => null) : null);
  return parsed.success ? { state: "signed-in", me: parsed.data, jwt } : { state: "unavailable" };
}

export async function callUserService(
  method: "GET" | "PUT" | "POST",
  path: string,
  jwt: string,
  body?: unknown,
): Promise<Response | null> {
  const service = userServiceUrl();
  if (!service) {
    return null;
  }
  return fetch(new URL(path, service), {
    method,
    headers: {
      Authorization: `Bearer ${jwt}`,
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
    signal: AbortSignal.timeout(8000),
  }).catch(() => null);
}

/**
 * A request that changes something must come from a page of this site. The
 * session cookie is SameSite=Lax, which already keeps it off a cross-site POST;
 * this is the second check, the one the old app lacked (SPEC.md §8).
 */
export function fromThisSite(request: Request): boolean {
  return request.headers.get("origin") === new URL(siteUrl()).origin;
}

/** The session cookie with the attributes it is always written and deleted with. */
export function sessionCookie(value: string, maxAge: number) {
  return {
    name: SESSION_COOKIE,
    value,
    httpOnly: true,
    secure: new URL(siteUrl()).protocol === "https:",
    sameSite: "lax" as const,
    path: "/",
    maxAge,
  };
}
