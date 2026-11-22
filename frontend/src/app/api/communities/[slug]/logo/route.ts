import { communityChanged } from "@/lib/communities";
import { communityCode } from "@/lib/community-answer";
import { communityServiceUrl } from "@/lib/env";
import { MAX_UPLOAD, refererLocale, toWebp } from "@/lib/images";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { localeOf, seeOther, text } from "@/lib/route-helpers";
import { currentSession, fromThisSite } from "@/lib/session";

/**
 * POST /api/communities/{slug}/logo, the picture form on the group's settings
 * page: intent "upload" with a file, or "remove". The upload becomes a 512 px
 * square WebP without metadata here (lib/images.ts); the community service
 * checks the role and stores the result.
 */
export async function POST(request: Request, { params }: RouteContext<"/api/communities/[slug]/logo">) {
  if (!fromThisSite(request)) {
    return Response.json({ error: "cross_site" }, { status: 403 });
  }
  const { slug } = await params;
  // Refused before the body is read; the form's language is in the page it came from.
  if (Number(request.headers.get("content-length") ?? 0) > MAX_UPLOAD + 64 * 1024) {
    const from = refererLocale(request);
    return seeOther(`${routes.manageCommunity(isLocale(from) ? from : "de", slug)}?error=TOO_LARGE`);
  }
  const form = await request.formData().catch(() => null);
  const locale = localeOf(form);
  const back = routes.manageCommunity(locale, slug);
  const session = await currentSession();
  if (session.state === "signed-out") {
    return seeOther(routes.signIn(locale, back));
  }
  if (session.state === "unavailable") {
    return seeOther(`${back}?error=UNAVAILABLE`);
  }
  const base = communityServiceUrl();
  if (!base) {
    return seeOther(`${back}?error=UNAVAILABLE`);
  }
  const path = new URL(`/v1/communities/${encodeURIComponent(slug)}/logo`, base);

  let response: Response | null;
  if (text(form, "intent") === "remove") {
    response = await fetch(`${path}/remove`, {
      method: "POST",
      headers: { Authorization: `Bearer ${session.jwt}` },
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
    }).catch(() => null);
  } else {
    const file = form?.get("logo");
    if (!(file instanceof File) || file.size === 0) {
      return seeOther(`${back}?error=NO_FILE`);
    }
    if (file.size > MAX_UPLOAD) {
      return seeOther(`${back}?error=TOO_LARGE`);
    }
    const webp = await toWebp(await file.arrayBuffer(), 512, 512);
    if (!webp) {
      return seeOther(`${back}?error=NOT_AN_IMAGE`);
    }
    response = await fetch(path, {
      method: "PUT",
      headers: { Authorization: `Bearer ${session.jwt}`, "Content-Type": "image/webp" },
      body: new Uint8Array(webp),
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
    }).catch(() => null);
  }
  if (!response?.ok) {
    const json = await response?.json().catch(() => null);
    return seeOther(`${back}?error=${response ? communityCode(json?.code) : "UNAVAILABLE"}`);
  }
  communityChanged(slug);
  return seeOther(`${back}?done=1`);
}

