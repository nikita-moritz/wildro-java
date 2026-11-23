import { ImageResponse } from "next/og";

import { defaultCover } from "@/lib/covers";
import { getDictionary } from "@/lib/dictionaries";
import { brand, siteUrl } from "@/lib/env";
import { getEvent } from "@/lib/events-api";
import { formatDate, formatNumber, formatTime } from "@/lib/format";
import { isLocale } from "@/lib/locales";
import { SHARE_FONT_FAMILY, SHARE_GREEN as GREEN, SHARE_SIZE, isShareFormat, pictureData, shareFonts, webpAnswer } from "@/lib/share-image";

/**
 * GET /{locale}/event/{slug}/image/{post|story}: the event as a picture for
 * Instagram (owner's plan, 2026-11-22), a post (1080 by 1350, the feed's tallest)
 * or a story (1080 by 1920). Photo above, facts below on a calm field, the site's
 * address at the foot, since Instagram links nothing in a caption. Drawn from
 * the event's own data, nothing from another site but our own media host.
 */
export async function GET(_request: Request, { params }: RouteContext<"/[locale]/event/[slug]/image/[format]">) {
  const { locale, slug, format } = await params;
  if (!isLocale(locale) || !isShareFormat(format)) {
    return new Response(null, { status: 404 });
  }
  const event = await getEvent(slug, locale).catch(() => null);
  if (!event || event.status !== "PUBLISHED") {
    return new Response(null, { status: 404 });
  }
  const t = getDictionary(locale);
  const { width, height } = SHARE_SIZE[format];
  const story = format === "story";
  const photoHeight = Math.round(height * (story ? 0.5 : 0.48));
  const photo = await pictureData(event.coverImageUrl ?? defaultCover(event.activityType)?.large, width, photoHeight);
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
      <div style={{ width, height, display: "flex", flexDirection: "column", background: "#ffffff", fontFamily: SHARE_FONT_FAMILY }}>
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
    { width, height, fonts: await shareFonts() },
  );
  return webpAnswer(png, `${slug}-${format}`);
}
