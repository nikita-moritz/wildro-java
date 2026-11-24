import Image from "next/image";

/**
 * An event's own cover in a card (list row, tile, home teaser). The file is
 * kept at 1600 by 900 for the event page and link previews, 150 to 300 KB; a
 * card shows a fraction of that, so next/image cuts it to the card's width and
 * serves WebP, cached on this server (next.config.ts, images). Fills its
 * parent, which is positioned and sized by the card.
 */
export function CoverThumb({
  url,
  credit,
  sizes,
  className,
}: {
  url: string;
  /** "Author, licence"; the event page carries the full credit. */
  credit: string | null;
  sizes: string;
  className?: string;
}) {
  return <Image src={url} alt="" title={credit ?? undefined} fill sizes={sizes} className={className} />;
}
