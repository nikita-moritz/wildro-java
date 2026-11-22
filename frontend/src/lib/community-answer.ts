import "server-only";

import { communityChanged, communityPageSchema } from "@/lib/communities";

const CODES = new Set([
  "NOT_FOUND",
  "FORBIDDEN",
  "TERMS_REQUIRED",
  "DISPLAY_NAME_REQUIRED",
  "LIMIT_OWNED",
  "INVALID",
  "WRONG_STATE",
  "OWNER_CANNOT_LEAVE",
  "TARGET_NOT_ADMIN",
  "NO_FILE",
  "TOO_LARGE",
  "NOT_AN_IMAGE",
  "UNAVAILABLE",
  "GENERIC",
]);

export type CommunityCode = "NOT_FOUND" | "FORBIDDEN" | "TERMS_REQUIRED" | "DISPLAY_NAME_REQUIRED" | "LIMIT_OWNED" |
  "INVALID" | "WRONG_STATE" | "OWNER_CANNOT_LEAVE" | "TARGET_NOT_ADMIN" | "NO_FILE" | "TOO_LARGE" | "NOT_AN_IMAGE" |
  "UNAVAILABLE" | "GENERIC";

export function communityCode(value: unknown): CommunityCode {
  return typeof value === "string" && CODES.has(value) ? (value as CommunityCode) : "GENERIC";
}

/** The editor's answer: {slug} on success (caches dropped), {code} on refusal. */
export async function communityAnswer(response: Response | null): Promise<Response> {
  if (!response) {
    return Response.json({ code: "UNAVAILABLE" }, { status: 503 });
  }
  const json = await response.json().catch(() => null);
  if (!response.ok) {
    const code = response.status === 400 ? "INVALID" : communityCode(json?.code);
    return Response.json({ code }, { status: response.status });
  }
  const page = communityPageSchema.safeParse(json);
  if (!page.success) {
    return Response.json({ code: "GENERIC" }, { status: 502 });
  }
  communityChanged(page.data.slug);
  return Response.json({ slug: page.data.slug });
}
