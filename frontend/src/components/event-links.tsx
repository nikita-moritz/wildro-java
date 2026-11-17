import { Link2 } from "lucide-react";
import { siFacebook, siInstagram, siTelegram, siWhatsapp, type SimpleIcon } from "simple-icons";

import type { EventPage } from "@/lib/events-api";

/**
 * The optional links beside an event: to the event itself on a network or in a
 * messenger, and to the community behind it. The four networks people actually
 * look for are drawn with their own mark and colour so they are recognised
 * before they are read; anything else is a plain link named by its host.
 *
 * Which network a link belongs to is decided by the event service
 * (LinkNetwork), not here: this component only maps the answer to a picture.
 * Plain links, rendered on the server: nothing reaches any of these networks
 * until the visitor clicks.
 */
type Link = EventPage["eventLinks"][number];
type KnownNetwork = Exclude<Link["network"], "OTHER">;

const NETWORK: Record<KnownNetwork, { name: string; icon: SimpleIcon }> = {
  TELEGRAM: { name: "Telegram", icon: siTelegram },
  INSTAGRAM: { name: "Instagram", icon: siInstagram },
  WHATSAPP: { name: "WhatsApp", icon: siWhatsapp },
  FACEBOOK: { name: "Facebook", icon: siFacebook },
};

/** "komoot.com" for https://www.komoot.com/tour/1: enough to say where the link goes. */
function hostOf(url: string): string {
  return new URL(url).hostname.replace(/^www\./, "");
}

export function EventLinks({ heading, links }: { heading: string; links: Link[] }) {
  if (links.length === 0) {
    return null;
  }
  return (
    <div className="flex flex-col gap-2">
      <h2 className="text-xs font-semibold tracking-widest text-muted-foreground uppercase">{heading}</h2>
      <ul className="flex flex-wrap gap-2">
        {links.map((link) => (
          <li key={link.url}>
            {/* nofollow for the same reason as the source link: a way to reach people, not an endorsement. */}
            <a
              href={link.url}
              target="_blank"
              rel="nofollow noopener noreferrer"
              className="inline-flex min-h-10 items-center gap-2 rounded-full border bg-card px-4 text-sm font-medium transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none"
            >
              {link.network === "OTHER" ? (
                <>
                  <Link2 className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                  {hostOf(link.url)}
                </>
              ) : (
                <>
                  <svg viewBox="0 0 24 24" className="h-4 w-4" fill={`#${NETWORK[link.network].icon.hex}`} aria-hidden="true">
                    <path d={NETWORK[link.network].icon.path} />
                  </svg>
                  {NETWORK[link.network].name}
                </>
              )}
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
