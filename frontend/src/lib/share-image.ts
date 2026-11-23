import "server-only";

import { readFile } from "node:fs/promises";
import { join } from "node:path";

import type { ImageResponse } from "next/og";
import sharp from "sharp";

/**
 * What every picture drawn for sharing has in common (an event's, a group's):
 * the formats, the fonts, pictures read in, and the answer as WebP.
 *
 * Inter is read from the package's files (traced into the build in
 * next.config.ts), Latin, Latin extended and Cyrillic, so every language draws.
 */

/** Instagram's post (the feed's tallest, 1080 by 1350) and story (1080 by 1920). */
export const SHARE_SIZE = { post: { width: 1080, height: 1350 }, story: { width: 1080, height: 1920 } } as const;

export type ShareFormat = keyof typeof SHARE_SIZE;

export function isShareFormat(value: string): value is ShareFormat {
  return value in SHARE_SIZE;
}

export const SHARE_GREEN = "#0d874a";

export const SHARE_FONT_FAMILY = "Inter, InterExt, InterCyr";

const FONT_DIR = join(process.cwd(), "node_modules/@fontsource/inter/files");

/**
 * One family name per subset, listed together in fontFamily: with one name for
 * all three the renderer took the first file and drew Cyrillic in a stand-in.
 */
const SUBSETS = { latin: "Inter", "latin-ext": "InterExt", cyrillic: "InterCyr" } as const;

let fonts: Promise<{ name: string; data: Buffer; weight: 400 | 700; style: "normal" }[]> | null = null;

export function shareFonts() {
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

/** A picture as a JPEG data address, cut to size: ours are WebP, which the renderer does not read. */
export async function pictureData(url: string | null | undefined, width: number, height: number): Promise<string | null> {
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

/**
 * The drawn picture as WebP, as every picture of the site (owner's rule): a few
 * hundred kilobytes instead of the renderer's megabytes of PNG.
 */
export async function webpAnswer(png: ImageResponse, filename: string): Promise<Response> {
  const webp = await sharp(Buffer.from(await png.arrayBuffer())).webp({ quality: 85 }).toBuffer();
  return new Response(new Uint8Array(webp), {
    headers: {
      "Content-Type": "image/webp",
      "Cache-Control": "public, max-age=3600",
      "Content-Disposition": `inline; filename="${filename.replace(/[^a-z0-9-]/g, "")}.webp"`,
      "X-Robots-Tag": "noindex",
    },
  });
}
