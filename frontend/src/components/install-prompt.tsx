"use client";

import { X } from "lucide-react";
import { useEffect, useState } from "react";

// Chrome's install event; not in the DOM typings yet.
type InstallEvent = Event & { prompt: () => Promise<void> };

export interface InstallText {
  title: string;
  body: string;
  iosSteps: [string, string, string];
  install: string;
  gotIt: string;
  close: string;
}

/**
 * "Add to home screen" on phones (app/manifest.ts makes the site installable),
 * taken over from evolution-space. Android and Chrome hand over their install
 * event, so the button opens the system dialog. iOS has no such API, and a
 * share sheet opened by the page lacks "Add to Home Screen" (only the browser's
 * own one has it), so an iPhone gets the steps, numbered, and no button that
 * pretends to install: the only button there closes the hint. A Share icon in
 * the text used to look like that button and did nothing when tapped. Shown a few seconds after load,
 * never inside the installed app. Closing it holds for this visit only:
 * remembering it would write to the device, which the privacy policy does not
 * list.
 */
export function InstallPrompt({ t, icon }: { t: InstallText; icon: string }) {
  const [mode, setMode] = useState<"android" | "ios" | null>(null);
  const [event, setEvent] = useState<InstallEvent | null>(null);

  useEffect(() => {
    const standalone =
      window.matchMedia("(display-mode: standalone)").matches ||
      (navigator as Navigator & { standalone?: boolean }).standalone;
    const phone = window.matchMedia("(max-width: 768px)").matches;
    if (standalone || !phone) {
      return;
    }

    let promptTimer: ReturnType<typeof setTimeout> | undefined;
    const onPrompt = (e: Event) => {
      e.preventDefault();
      setEvent(e as InstallEvent);
      promptTimer = setTimeout(() => setMode("android"), 4000);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);

    // Every iOS browser: Safari, and since iOS 16.4 Chrome, Edge and Firefox add to the home screen too.
    const ios = /iPhone|iPad|iPod/.test(navigator.userAgent);
    const iosTimer = ios ? setTimeout(() => setMode("ios"), 4000) : undefined;

    return () => {
      window.removeEventListener("beforeinstallprompt", onPrompt);
      clearTimeout(promptTimer);
      clearTimeout(iosTimer);
    };
  }, []);

  if (!mode) {
    return null;
  }

  const install = async () => {
    await event?.prompt();
    setMode(null);
  };

  return (
    <div role="dialog" aria-label={t.title} className="fixed inset-x-3 bottom-3 z-[1000] rounded-2xl border bg-card p-5 shadow-xl">
      <button
        type="button"
        onClick={() => setMode(null)}
        aria-label={t.close}
        className="absolute top-2 right-2 flex h-11 w-11 items-center justify-center text-muted-foreground"
      >
        <X className="h-5 w-5" aria-hidden />
      </button>
      <div className="flex items-start gap-4 pr-8">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={icon} alt="" width={48} height={48} className="h-12 w-12 shrink-0 rounded-xl" />
        <div>
          <p className="text-base font-semibold">{t.title}</p>
          {mode === "ios" ? (
            <ol className="mt-2 flex list-decimal flex-col gap-1 pl-4 text-sm text-muted-foreground">
              {t.iosSteps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
          ) : (
            <p className="mt-1 text-sm text-muted-foreground">{t.body}</p>
          )}
        </div>
      </div>
      {mode === "android" && (
        <button
          type="button"
          onClick={install}
          className="mt-4 min-h-11 w-full rounded-full bg-primary text-sm font-medium text-primary-foreground"
        >
          {t.install}
        </button>
      )}
      {mode === "ios" && (
        <button
          type="button"
          onClick={() => setMode(null)}
          className="mt-4 min-h-11 w-full rounded-full border text-sm font-medium"
        >
          {t.gotIt}
        </button>
      )}
    </div>
  );
}
