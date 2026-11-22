"use client";

import { CalendarPlus, Check, ChevronDown, Share2 } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import type { Dictionary } from "@/lib/dictionaries";

/**
 * Share and add to calendar, under an upcoming event's title. On a phone
 * "Teilen" opens the system's share sheet (WhatsApp, Telegram, Instagram and the
 * rest are there); where there is none, it copies the link. The calendar menu
 * offers Google's form and the .ics file the event service writes, which
 * Apple's Calendar and Outlook open.
 */
export function ShareBar({
  title,
  line,
  url,
  googleUrl,
  icsUrl,
  t,
}: {
  title: string;
  /** Date and place in one line, the text that goes with the link. */
  line: string;
  url: string;
  googleUrl: string;
  icsUrl: string;
  t: Dictionary["share"];
}) {
  const [copied, setCopied] = useState(false);

  async function share() {
    if (typeof navigator.share === "function") {
      // Closing the sheet rejects; that is the visitor's choice, not an error.
      await navigator.share({ title, text: `${title}\n${line}`, url }).catch(() => {});
      return;
    }
    await navigator.clipboard?.writeText(url).catch(() => {});
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  }

  return (
    <div className="flex flex-wrap gap-2">
      <Button type="button" onClick={share} className="h-11 gap-2 rounded-full px-4">
        {copied ? <Check className="h-4 w-4" aria-hidden /> : <Share2 className="h-4 w-4" aria-hidden />}
        {copied ? t.copied : t.share}
      </Button>
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
      <span role="status" aria-live="polite" className="sr-only">
        {copied ? t.copied : ""}
      </span>
    </div>
  );
}
