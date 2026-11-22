import type { Metadata } from "next";
import { notFound } from "next/navigation";
import QRCode from "qrcode";

import { BackLink } from "@/components/back-link";
import { PrintButton } from "@/components/share/print-button";
import { defaultCover } from "@/lib/covers";
import { getDictionary } from "@/lib/dictionaries";
import { brand, siteUrl } from "@/lib/env";
import { getEvent } from "@/lib/events-api";
import { formatDate, formatNumber, formatTime } from "@/lib/format";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { absoluteUrl } from "@/lib/seo";

export async function generateMetadata({ params }: PageProps<"/[locale]/event/[slug]/poster">): Promise<Metadata> {
  const { locale, slug } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const event = await getEvent(slug, locale);
  return { title: event ? `${getDictionary(locale).share.poster}: ${event.name}` : undefined, robots: { index: false, follow: false } };
}

// Rendered per request like the event page: its address is not known at build time.
export const instant = false;

const GREEN = "#0d874a";

/**
 * An event as an A4 poster for a notice board (owner's plan, 2026-11-22): the
 * photo, the facts large, and a QR code to the event's page, since paper has
 * no links. Printed from the browser, which also saves it as a PDF.
 *
 * The sheet is drawn in its own colours, never the theme's, so it prints the
 * same from a dark screen. Sizes are in container units of the sheet, so the
 * preview on a phone is the printed page made small, not a different layout.
 */
export default async function PosterPage({ params }: PageProps<"/[locale]/event/[slug]/poster">) {
  const { locale, slug } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const event = await getEvent(slug, locale);
  if (!event || event.status !== "PUBLISHED") {
    notFound();
  }
  const t = getDictionary(locale);
  const url = absoluteUrl(routes.event(locale, event.slug));
  const qr = await QRCode.toString(url, { type: "svg", margin: 0, errorCorrectionLevel: "M", color: { dark: "#0a0a0a", light: "#ffffff" } });
  const photo = event.coverImageUrl ?? defaultCover(event.activityType)?.large ?? null;
  const facts = [
    t.activity[event.activityType],
    event.distanceKm === null ? null : `${formatNumber(event.distanceKm, locale)} km`,
    event.difficulty === null ? null : t.difficulty[event.difficulty],
  ]
    .filter(Boolean)
    .join(" · ");
  const host = new URL(siteUrl()).host;

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-4 px-4 py-6 print:m-0 print:max-w-none print:p-0">
      {/* A4 without the browser's margins; the sheet fills it. */}
      <style>{"@page { size: A4; margin: 0; } @media print { html, body { background: #fff !important; } }"}</style>
      <div className="flex flex-col gap-3 print:hidden">
        <BackLink href={routes.event(locale, event.slug)} label={t.event.back} />
        <div className="flex flex-wrap items-center gap-3">
          <PrintButton label={t.share.print} />
          <p className="text-sm text-muted-foreground">{t.share.posterHint}</p>
        </div>
      </div>

      <article
        className="@container mx-auto flex aspect-[210/297] w-full flex-col overflow-hidden border shadow-lg print:h-[297mm] print:w-[210mm] print:border-0 print:shadow-none"
        style={{ background: "#ffffff", color: "#0a0a0a", printColorAdjust: "exact", WebkitPrintColorAdjust: "exact", fontFamily: "Inter, sans-serif" }}
      >
        {photo ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={photo} alt="" className="h-[30%] w-full shrink-0 object-cover" />
        ) : (
          <div className="h-[30%] w-full shrink-0" style={{ background: GREEN }} />
        )}
        <div className="flex min-h-0 flex-1 flex-col gap-[2cqw] p-[6cqw]">
          {/* Nothing in this column shrinks but the description, and that only by whole lines. */}
          <div className="flex shrink-0">
            <span className="rounded-full px-[2.4cqw] py-[0.8cqw] text-[2.6cqw] font-bold" style={{ color: GREEN, border: `0.35cqw solid ${GREEN}` }}>
              {facts}
            </span>
          </div>
          <h1 lang={event.originalLocale ?? locale} className="line-clamp-3 shrink-0 text-[7cqw] leading-[1.08] font-bold">
            {event.name}
          </h1>
          <div className="flex shrink-0 flex-col gap-[0.6cqw]">
            <p className="text-[3.8cqw] font-semibold">{`${formatDate(event.startsAt, locale)}, ${formatTime(event.startsAt, locale)}`}</p>
            <p className="text-[3.1cqw]" style={{ color: "#404040" }}>
              {event.placeName}
            </p>
          </div>
          <p lang={event.originalLocale ?? locale} className="line-clamp-3 shrink-0 text-[2.5cqw] leading-snug" style={{ color: "#404040" }}>
            {event.description}
          </p>
          <div className="min-h-0 flex-1" />
          <div className="flex shrink-0 items-end justify-between gap-[4cqw]">
            <div className="flex min-w-0 flex-col gap-[0.6cqw]">
              {event.organizer && (
                <p className="text-[2.4cqw]" style={{ color: "#404040" }}>
                  {t.event.organizer}: {event.organizer}
                </p>
              )}
              <p className="text-[3.6cqw] font-bold" style={{ color: GREEN }}>
                {brand().name}
              </p>
              <p className="text-[2.4cqw] break-all" style={{ color: "#404040" }}>
                {host}
              </p>
            </div>
            <figure className="flex shrink-0 flex-col items-center gap-[1cqw]">
              <div className="w-[19cqw] [&>svg]:h-auto [&>svg]:w-full" dangerouslySetInnerHTML={{ __html: qr }} />
              <figcaption className="text-[2.2cqw] font-semibold">{t.share.more}</figcaption>
            </figure>
          </div>
        </div>
      </article>
    </main>
  );
}
