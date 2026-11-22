import "server-only";

import sharp from "sharp";

/** What a phone photo may weigh before it is scaled down; the stored picture is far smaller. */
export const MAX_UPLOAD = 8 * 1024 * 1024;

/** What phones and cameras send; HEIF covers the iPhone's HEIC. */
const RASTER = new Set(["jpeg", "png", "webp", "heif", "avif", "gif", "tiff"]);

/**
 * An uploaded picture as the site keeps it: raster formats only (an SVG is a
 * document that can point at other files), turned upright, cut to the size
 * asked for around what matters in it, written as WebP. sharp writes no
 * metadata unless asked, so GPS and camera data never get past this step.
 * null for anything it cannot read as one still picture of sane size.
 *
 * One place for every upload: a group's picture (512 square) and an event's
 * (1600 by 900, the covers' shape).
 */
export async function toWebp(input: ArrayBuffer, width: number, height: number): Promise<Buffer | null> {
  const options = { failOn: "error", limitInputPixels: 50_000_000, animated: false } as const;
  try {
    const { format } = await sharp(Buffer.from(input), options).metadata();
    if (!format || !RASTER.has(format)) {
      return null;
    }
    return await sharp(Buffer.from(input), options)
      .rotate()
      .resize(width, height, { fit: "cover", position: "attention" })
      .webp({ quality: 82 })
      .toBuffer();
  } catch {
    return null;
  }
}

/** The form's language when a body too large is refused before it is read: from the page it came from. */
export function refererLocale(request: Request): string {
  return URL.parse(request.headers.get("referer") ?? "")?.pathname.split("/")[1] ?? "de";
}
