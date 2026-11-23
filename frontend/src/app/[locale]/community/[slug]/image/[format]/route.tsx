import { ImageResponse } from "next/og";

import { getCommunity, getCommunityEvents } from "@/lib/communities";
import { getDictionary } from "@/lib/dictionaries";
import { brand, siteUrl } from "@/lib/env";
import { formatCount, formatTime, formatWeekdayDate } from "@/lib/format";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { SHARE_FONT_FAMILY, SHARE_GREEN as GREEN, SHARE_SIZE, isShareFormat, pictureData, shareFonts, webpAnswer } from "@/lib/share-image";

/** How many outings fit: a post is shorter than a story. */
const ROWS = { post: 5, story: 8 } as const;

/**
 * GET /{locale}/community/{slug}/image/{post|story}: a group's digest as a
 * picture for chats and Instagram (stage 2, 2026-11-23): picture, name and area
 * above, its next outings as a list, the group's address at the foot. Only for
 * an active group with something ahead: there is nothing to show otherwise.
 */
export async function GET(_request: Request, { params }: RouteContext<"/[locale]/community/[slug]/image/[format]">) {
  const { locale, slug, format } = await params;
  if (!isLocale(locale) || !isShareFormat(format)) {
    return new Response(null, { status: 404 });
  }
  const community = await getCommunity(slug).catch(() => null);
  if (!community || community.status !== "ACTIVE") {
    return new Response(null, { status: 404 });
  }
  const events = (await getCommunityEvents(community.id, locale).catch(() => [])).slice(0, ROWS[format]);
  if (events.length === 0) {
    return new Response(null, { status: 404 });
  }
  const t = getDictionary(locale);
  const { width, height } = SHARE_SIZE[format];
  const story = format === "story";
  const logo = await pictureData(community.logoUrl, 176, 176);
  const address = `${new URL(siteUrl()).host}${routes.community(locale, community.slug)}`;
  const nameSize = community.name.length > 30 ? 54 : 66;

  const png = new ImageResponse(
    (
      <div
        style={{
          width,
          height,
          display: "flex",
          flexDirection: "column",
          background: "#ffffff",
          fontFamily: SHARE_FONT_FAMILY,
          padding: story ? "120px 80px 96px" : "80px 72px 64px",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 36 }}>
          {logo ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={logo} width={160} height={160} alt="" style={{ borderRadius: 999, objectFit: "cover" }} />
          ) : (
            <div
              style={{
                width: 160,
                height: 160,
                borderRadius: 999,
                background: GREEN,
                color: "#ffffff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 72,
                fontWeight: 700,
              }}
            >
              {community.name.slice(0, 1).toUpperCase()}
            </div>
          )}
          <div style={{ display: "flex", flexDirection: "column", gap: 10, flex: 1 }}>
            <div style={{ display: "flex", fontSize: nameSize, fontWeight: 700, lineHeight: 1.1, color: "#0a0a0a" }}>
              {community.name}
            </div>
            <div style={{ display: "flex", fontSize: 32, color: "#525252" }}>
              {`${community.area} · ${formatCount(t.community.members, community.memberCount, locale)}`}
            </div>
          </div>
        </div>

        <div style={{ display: "flex", marginTop: story ? 96 : 64, fontSize: 36, fontWeight: 700, color: GREEN }}>
          {t.share.upcomingHeading}
        </div>
        <div style={{ display: "flex", flexDirection: "column", marginTop: 20 }}>
          {events.map((event) => (
            <div
              key={event.slug}
              style={{ display: "flex", gap: 32, padding: story ? "30px 0" : "24px 0", borderTop: "2px solid #e5e5e5" }}
            >
              <div style={{ display: "flex", flexDirection: "column", width: 250, flexShrink: 0, gap: 4 }}>
                <div style={{ display: "flex", fontSize: 32, fontWeight: 700, color: "#0a0a0a" }}>
                  {formatWeekdayDate(event.startsAt, locale)}
                </div>
                <div style={{ display: "flex", fontSize: 28, color: "#525252" }}>{formatTime(event.startsAt, locale)}</div>
              </div>
              <div style={{ display: "flex", flexDirection: "column", flex: 1, gap: 4 }}>
                <div style={{ display: "flex", fontSize: 34, fontWeight: 700, lineHeight: 1.2, color: "#0a0a0a" }}>
                  {event.name.length > 70 ? `${event.name.slice(0, 68)}…` : event.name}
                </div>
                <div style={{ display: "flex", fontSize: 28, color: "#525252" }}>{event.placeName}</div>
              </div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1 }} />
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", fontSize: 30 }}>
          <div style={{ display: "flex", fontWeight: 700, color: GREEN }}>{brand().name}</div>
          <div style={{ display: "flex", color: "#525252" }}>{address}</div>
        </div>
      </div>
    ),
    { width, height, fonts: await shareFonts() },
  );
  return webpAnswer(png, `${community.slug}-${format}`);
}
