/**
 * The visitor's address as Caddy saw it. Caddy appends the peer to
 * X-Forwarded-For, so the rightmost entry is the real one; reading the leftmost
 * would let anyone send "X-Forwarded-For: 1.2.3.4" and mint a visitor per
 * request, defeating every rate limit that keys on it.
 */
export function clientAddress(request: Request): string | null {
  const forwarded = request.headers.get("x-forwarded-for");
  if (!forwarded) {
    return null;
  }
  const parts = forwarded
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean);
  return parts[parts.length - 1] ?? null;
}
