import { readFile } from "node:fs/promises";
import { join } from "node:path";

import { ImageResponse } from "next/og";
import sharp from "sharp";

import { defaultCover } from "@/lib/covers";
import { getDictionary } from "@/lib/dictionaries";
import { brand, siteUrl } from "@/lib/env";
import { getEvent } from "@/lib/events-api";
import { formatDate, formatNumber, formatTime } from "@/lib/format";
import { isLocale } from "@/lib/locales";

/**
 * GET /{locale}/event/{slug}/image/{post|story}: the event as a picture for
 * Instagram (owner's plan, 2026-11-22), a post (1080 by 1350, the feed's tallest)
 * or a story (1080 by 1920). Photo above, facts below on a calm field, the site's
 * address at the foot, since Instagram links nothing in a caption.
 *
 * Drawn here from the event's own data, nothing from another site but our own
 * media host. Inter is read from the package's files (traced into the build in
 * next.config.ts), Latin, Latin extended and Cyrillic, so every language draws.
 */
const SIZE = { post: { width: 1080, height: 1350 }, story: { width: 1080, height: 1920 } } as const;

type Format = keyof typeof SIZE;

const GREEN = "#0d874a";

const FONT_DIR = join(process.cwd(), "node_modules/@fontsource/inter/files");

let fonts: Promise<{ name: string; data: Buffer; weight: 400 | 700; style: "normal" }[]> | null = null;

/**
 * One family name per subset, listed together in fontFamily: with one name for
 * all three the renderer took the first file and drew Cyrillic in a stand-in.
 */
const SUBSETS = { latin: "Inter", "latin-ext": "InterExt", cyrillic: "InterCyr" } as const;

function loadFonts() {
  fonts ??= Promise.all(
    (Object.keys(SUBSETS) as (keyof typeof SUBSETS)[]).flatMap((subset) =>
      ([400, 700] as const).map(async (weight) => ({
        name: SUBSETS[subset],
        data: await readFile(join(FONT_DIR, `inter-${subset}-${weight}-normal.woff`)),
        weight,
        style: "normal" as const,
      })),
    ),
  );
  return fonts;
}

/** The cover as a JPEG data address: the picture is WebP, which the renderer does not read. */
async function cover(url: string | null | undefined, width: number, height: number): Promise<string | null> {
  if (!url) {
    return null;
  }
  const response = await fetch(url, { signal: AbortSignal.timeout(8000) }).catch(() => null);
  if (!response?.ok) {
    return null;
  }
  const jpeg = await sharp(Buffer.from(await response.arrayBuffer()))
    .resize(width, height, { fit: "cover", position: "attention" })
    .jpeg({ quality: 82 })
    .toBuffer()
    .catch(() => null);
  return jpeg ? `data:image/jpeg;base64,${jpeg.toString("base64")}` : null;
}

export async function GET(_request: Request, { params }: RouteContext<"/[locale]/event/[slug]/image/[format]">) {
  const { locale, slug, format } = await params;
  if (!isLocale(locale) || !(format in SIZE)) {
    return new Response(null, { status: 404 });
  }
  const event = await getEvent(slug, locale).catch(() => null);
  if (!event || event.status !== "PUBLISHED") {
    return new Response(null, { status: 404 });
  }
  const t = getDictionary(locale);
  const { width, height } = SIZE[format as Format];
  const story = format === "story";
  const photoHeight = Math.round(height * (story ? 0.5 : 0.48));
  const photo = await cover(event.coverImageUrl ?? defaultCover(event.activityType)?.large, width, photoHeight);
  const facts = [
    t.activity[event.activityType],
    event.distanceKm === null ? null : `${formatNumber(event.distanceKm, locale)} km`,
    event.difficulty === null ? null : t.difficulty[event.difficulty],
  ]
    .filter(Boolean)
    .join(" · ");
  const host = new URL(siteUrl()).host;
  const titleSize = event.name.length > 60 ? (story ? 72 : 60) : story ? 88 : 74;

  const png = new ImageResponse(
    (
      <div style={{ width, height, display: "flex", flexDirection: "column", background: "#ffffff", fontFamily: "Inter, InterExt, InterCyr" }}>
        {photo ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={photo} width={width} height={photoHeight} alt="" style={{ objectFit: "cover" }} />
        ) : (
          <div style={{ width, height: photoHeight, background: GREEN }} />
        )}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", padding: story ? "72px 80px" : "56px 72px", gap: story ? 32 : 24 }}>
          <div style={{ display: "flex" }}>
            <div
              style={{
                display: "flex",
                fontSize: story ? 34 : 30,
                fontWeight: 700,
                color: GREEN,
                border: `3px solid ${GREEN}`,
                borderRadius: 999,
                padding: "8px 24px",
              }}
            >
              {facts}
            </div>
          </div>
          <div style={{ display: "flex", fontSize: titleSize, fontWeight: 700, lineHeight: 1.1, color: "#0a0a0a" }}>
            {event.name}
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 10, fontSize: story ? 44 : 38, color: "#262626" }}>
            <div style={{ display: "flex" }}>{`${formatDate(event.startsAt, locale)}, ${formatTime(event.startsAt, locale)}`}</div>
            <div style={{ display: "flex", color: "#525252" }}>{event.placeName}</div>
          </div>
          <div style={{ flex: 1 }} />
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", fontSize: story ? 38 : 32 }}>
            <div style={{ display: "flex", fontWeight: 700, color: GREEN }}>{brand().name}</div>
            <div style={{ display: "flex", color: "#525252" }}>{host}</div>
          </div>
        </div>
      </div>
    ),
    { width, height, fonts: await loadFonts() },
  );
  // WebP, as every picture of the site (owner's rule): a few hundred
  // kilobytes instead of the renderer's megabytes of PNG.
  const webp = await sharp(Buffer.from(await png.arrayBuffer())).webp({ quality: 85 }).toBuffer();
  return new Response(new Uint8Array(webp), {
    headers: {
      "Content-Type": "image/webp",
      "Cache-Control": "public, max-age=3600",
      "Content-Disposition": `inline; filename="${slug.replace(/[^a-z0-9-]/g, "")}-${format}.webp"`,
      "X-Robots-Tag": "noindex",
    },
  });
}
