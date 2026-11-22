import { eventServiceUrl } from "@/lib/env";
import { MAX_UPLOAD, refererLocale, toWebp } from "@/lib/images";
import { isLocale } from "@/lib/locales";
import { errorCode, publicChanged } from "@/lib/own-events";
import { routes } from "@/lib/routes";
import { localeOf, seeOther as redirectTo, text } from "@/lib/route-helpers";
import { currentSession, fromThisSite } from "@/lib/session";

/**
 * POST /api/own/events/{slug}/cover, the picture form on an own event's edit
 * page: intent "upload" with a file, or "remove". The upload becomes a 1600 by
 * 900 WebP without metadata here (lib/images.ts), the covers' shape; the event
 * service checks who may and stores it. A 303 back to the page, or with
 * reply "json" (the editor, right after creating the event) {ok} or {code}.
 */
export async function POST(request: Request, { params }: RouteContext<"/api/own/events/[slug]/cover">) {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const { slug } = await params;
  // Refused before the body is read; the form's language is in the page it came from.
  if (Number(request.headers.get("content-length") ?? 0) > MAX_UPLOAD + 64 * 1024) {
    const from = refererLocale(request);
    return redirectTo(`${routes.editEvent(isLocale(from) ? from : "de", slug)}?error=TOO_LARGE`);
  }
  const form = await request.formData().catch(() => null);
  const locale = localeOf(form);
  const page = routes.editEvent(locale, slug);
  const json = text(form, "reply") === "json";
  const seeOther = (path: string) => {
    if (!json) {
      return redirectTo(path);
    }
    const code = new URL(path, "http://x").searchParams.get("error");
    return code ? Response.json({ code }, { status: 400 }) : Response.json({ ok: true });
  };
  const session = await currentSession();
  if (session.state === "signed-out") {
    return json ? Response.json({ code: "UNAVAILABLE" }, { status: 401 }) : redirectTo(routes.signIn(locale, page));
  }
  if (session.state === "unavailable") {
    return seeOther(`${page}?error=UNAVAILABLE`);
  }
  const base = new URL(`/v1/own/events/${encodeURIComponent(slug)}/cover`, eventServiceUrl());

  let response: Response | null;
  if (text(form, "intent") === "remove") {
    response = await fetch(`${base}/remove`, {
      method: "POST",
      headers: { Authorization: `Bearer ${session.jwt}` },
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
    }).catch(() => null);
  } else {
    const file = form?.get("cover");
    if (!(file instanceof File) || file.size === 0) {
      return seeOther(`${page}?error=NO_FILE`);
    }
    if (file.size > MAX_UPLOAD) {
      return seeOther(`${page}?error=TOO_LARGE`);
    }
    const webp = await toWebp(await file.arrayBuffer(), 1600, 900);
    if (!webp) {
      return seeOther(`${page}?error=NOT_AN_IMAGE`);
    }
    response = await fetch(base, {
      method: "PUT",
      headers: { Authorization: `Bearer ${session.jwt}`, "Content-Type": "image/webp" },
      body: new Uint8Array(webp),
      cache: "no-store",
      signal: AbortSignal.timeout(15000),
    }).catch(() => null);
  }
  if (!response?.ok) {
    const json = await response?.json().catch(() => null);
    return seeOther(`${page}?error=${response ? errorCode(json?.code) : "UNAVAILABLE"}`);
  }
  publicChanged(slug);
  return seeOther(`${page}?done=cover`);
}
