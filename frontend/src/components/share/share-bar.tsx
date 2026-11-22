"use client";

import { CalendarPlus, Check, ChevronDown, Copy, Download, Link2, Share2, Smartphone } from "lucide-react";
import { useState, useSyncExternalStore } from "react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import type { Dictionary } from "@/lib/dictionaries";
import { type Channel, facebookUrl, telegramUrl, whatsappUrl } from "@/lib/share";
import { cn } from "@/lib/utils";

const CHANNELS: Channel[] = ["whatsapp", "telegram", "facebook", "instagram"];

const subscribe = () => () => {};

/**
 * Share and add to calendar, under an upcoming event's title.
 *
 * "Teilen" opens a panel: the phone's own share sheet where there is one, the
 * link to copy, and a ready text per channel to post in a chat or a group. The
 * text can be changed before it goes; WhatsApp and Telegram open with it filled
 * in, Facebook takes only links, so its text is copied and pasted. Nothing is
 * loaded from those sites before a press. The calendar menu offers Google's form
 * and the .ics the event service writes.
 */
export function ShareBar({
  title,
  line,
  url,
  texts,
  googleUrl,
  icsUrl,
  images,
  t,
}: {
  title: string;
  /** Date and place in one line, what goes with the link in the share sheet. */
  line: string;
  url: string;
  /** The ready texts, written on the server in the page's language. */
  texts: Record<Channel, string>;
  googleUrl: string;
  icsUrl: string;
  /** The Instagram pictures of the event, drawn on request: post and story. */
  images: { post: string; story: string };
  t: Dictionary["share"];
}) {
  const [channel, setChannel] = useState<Channel>("whatsapp");
  const [edited, setEdited] = useState<Record<Channel, string>>(texts);
  const [copied, setCopied] = useState<"link" | "text" | null>(null);
  const canShare = useSyncExternalStore(
    subscribe,
    () => typeof navigator.share === "function",
    () => false,
  );
  const text = edited[channel];

  async function copy(value: string, what: "link" | "text") {
    await navigator.clipboard?.writeText(value).catch(() => {});
    setCopied(what);
    setTimeout(() => setCopied(null), 2500);
  }

  /** The picture itself into the share sheet (Instagram, WhatsApp); a download where that is not possible. */
  async function shareImage(src: string, name: string) {
    const blob = await fetch(src).then((response) => (response.ok ? response.blob() : null)).catch(() => null);
    if (!blob) {
      return;
    }
    const file = new File([blob], name, { type: blob.type || "image/webp" });
    if (typeof navigator.canShare === "function" && navigator.canShare({ files: [file] })) {
      await navigator.share({ files: [file], text: edited.instagram }).catch(() => {});
      return;
    }
    const link = Object.assign(document.createElement("a"), { href: URL.createObjectURL(blob), download: name });
    link.click();
    URL.revokeObjectURL(link.href);
  }

  function openChannel() {
    if (channel === "whatsapp") {
      window.open(whatsappUrl(text), "_blank", "noopener,noreferrer");
    } else if (channel === "telegram") {
      window.open(telegramUrl(url, text), "_blank", "noopener,noreferrer");
    } else {
      // Facebook keeps no text from outside: it goes to the clipboard first.
      void copy(text, "text");
      window.open(facebookUrl(url), "_blank", "noopener,noreferrer");
    }
  }

  return (
    <div className="flex flex-wrap gap-2">
      <Dialog>
        <DialogTrigger asChild>
          <Button type="button" className="h-11 gap-2 rounded-full px-4">
            <Share2 className="h-4 w-4" aria-hidden />
            {t.share}
          </Button>
        </DialogTrigger>
        <DialogContent closeLabel={t.close}>
          <DialogTitle>{t.title}</DialogTitle>
          <DialogDescription className="sr-only">{title}</DialogDescription>

          <div className="flex flex-wrap gap-2">
            {canShare && (
              <Button
                type="button"
                variant="outline"
                className="h-11 gap-2 rounded-full"
                onClick={() => void navigator.share({ title, text: `${title}\n${line}`, url }).catch(() => {})}
              >
                <Smartphone className="h-4 w-4" aria-hidden />
                {t.viaPhone}
              </Button>
            )}
            <Button type="button" variant="outline" className="h-11 gap-2 rounded-full" onClick={() => void copy(url, "link")}>
              {copied === "link" ? <Check className="h-4 w-4" aria-hidden /> : <Link2 className="h-4 w-4" aria-hidden />}
              {copied === "link" ? t.copied : t.copyLink}
            </Button>
          </div>

          <div className="flex flex-col gap-3 border-t pt-4">
            <div role="tablist" aria-label={t.textFor} className="grid grid-cols-4 gap-1 rounded-full bg-muted p-1">
              {CHANNELS.map((each) => (
                <button
                  key={each}
                  type="button"
                  role="tab"
                  aria-selected={each === channel}
                  onClick={() => setChannel(each)}
                  className={cn(
                    "min-h-10 min-w-0 truncate rounded-full px-1 text-[13px] font-medium transition-colors sm:px-3 sm:text-sm",
                    each === channel ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground",
                  )}
                >
                  {t.channels[each]}
                </button>
              ))}
            </div>
            {channel === "instagram" && (
              // Drawn only once this tab is open: nobody waits for pictures they did not ask for.
              <div className="flex flex-col gap-3">
                <p className="text-xs text-muted-foreground">{t.instagramHint}</p>
                <div className="grid grid-cols-2 gap-3">
                  {(["post", "story"] as const).map((format) => (
                    <figure key={format} className="flex flex-col gap-2">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={images[format]}
                        alt=""
                        loading="lazy"
                        className={cn("w-full rounded-lg border bg-muted object-cover", format === "post" ? "aspect-[4/5]" : "aspect-[9/16]")}
                      />
                      <figcaption className="text-xs font-medium">{format === "post" ? t.imagePost : t.imageStory}</figcaption>
                      <Button
                        type="button"
                        variant="outline"
                        className="h-11 gap-2 rounded-full px-3"
                        onClick={() => void shareImage(images[format], `${format}.webp`)}
                      >
                        {canShare ? <Share2 className="h-4 w-4" aria-hidden /> : <Download className="h-4 w-4" aria-hidden />}
                        {canShare ? t.shareImage : t.download}
                      </Button>
                    </figure>
                  ))}
                </div>
              </div>
            )}
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-muted-foreground">{t.editHint}</span>
              <textarea
                value={text}
                onChange={(event) => setEdited({ ...edited, [channel]: event.target.value })}
                rows={9}
                className="w-full rounded-lg border bg-background px-3 py-2 text-base"
              />
            </label>
            {channel === "facebook" && <p className="text-xs text-muted-foreground">{t.facebookHint}</p>}
            <div className="flex flex-wrap gap-2">
              {channel !== "instagram" && (
                <Button type="button" className="h-11 rounded-full" onClick={openChannel}>
                  {t.open[channel]}
                </Button>
              )}
              <Button type="button" variant="outline" className="h-11 gap-2 rounded-full" onClick={() => void copy(text, "text")}>
                {copied === "text" ? <Check className="h-4 w-4" aria-hidden /> : <Copy className="h-4 w-4" aria-hidden />}
                {copied === "text" ? t.textCopied : t.copyText}
              </Button>
            </div>
          </div>
          <span role="status" aria-live="polite" className="sr-only">
            {copied === "link" ? t.copied : copied === "text" ? t.textCopied : ""}
          </span>
        </DialogContent>
      </Dialog>

      <DropdownMenu modal={false}>
        <DropdownMenuTrigger asChild>
          <Button type="button" variant="outline" className="h-11 gap-2 rounded-full px-4 data-[state=open]:bg-muted">
            <CalendarPlus className="h-4 w-4" aria-hidden />
            {t.calendar}
            <ChevronDown className="h-3.5 w-3.5 opacity-60" aria-hidden />
          </Button>
        </DropdownMenuTrigger>
        {/* Not in the header: here the list may shift to stay on a narrow screen. */}
        <DropdownMenuContent align="start" avoidCollisions collisionPadding={12} className="max-w-[calc(100vw-24px)]">
          <DropdownMenuItem asChild>
            <a href={googleUrl} target="_blank" rel="noopener noreferrer">
              {t.google}
            </a>
          </DropdownMenuItem>
          <DropdownMenuItem asChild>
            <a href={icsUrl} download>
              {t.ics}
            </a>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
