import { cn } from "@/lib/utils";

/**
 * A group's picture, round, or its first letter on a calm field when it has
 * none, so every group in a list has the same shape in the same place. The
 * picture is a 512 px WebP from our own server; a plain img, no optimiser in
 * between, since it is already the size it is shown at.
 */
export function CommunityLogo({ url, name, size = 48, className }: { url: string | null; name: string; size?: number; className?: string }) {
  const style = { width: size, height: size };
  if (url) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img src={url} alt="" width={size} height={size} loading="lazy" decoding="async" style={style} className={cn("shrink-0 rounded-full border bg-muted object-cover", className)} />
    );
  }
  return (
    <span
      aria-hidden="true"
      style={{ ...style, fontSize: Math.round(size * 0.42) }}
      className={cn("flex shrink-0 items-center justify-center rounded-full border bg-muted font-semibold text-muted-foreground", className)}
    >
      {name.trim().charAt(0).toUpperCase() || "?"}
    </span>
  );
}
