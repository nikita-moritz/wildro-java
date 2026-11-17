"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";

/**
 * Nothing of Mapbox loads until the visitor asks for the map. Opening it sends
 * their address and usage events to a company in the United States, and that is
 * a choice for them to make, not a side effect of following a link (Art. 6
 * Abs. 1 lit. a DSGVO, § 25 Abs. 1 TDDDG). The choice is remembered on this
 * device only, and the privacy page has the button that takes it back.
 *
 * It also keeps the bill honest: Mapbox counts every initialised map, and a
 * crawler or a visitor who bounces never initialises one.
 */
export const MAP_CONSENT_KEY = "wildro-map-consent";

export interface MapConsentText {
  title: string;
  body: string;
  button: string;
  privacyLink: string;
}

type Choice = "unknown" | "asked" | "granted";

export function MapConsent({
  privacyHref,
  t,
  children,
}: {
  privacyHref: string;
  t: MapConsentText;
  children: React.ReactNode;
}) {
  // "unknown" until the browser has been asked, so the server render and the
  // first client render agree and a returning visitor never sees the question flash.
  const [choice, setChoice] = useState<Choice>("unknown");

  useEffect(() => {
    let granted = false;
    try {
      granted = window.localStorage.getItem(MAP_CONSENT_KEY) === "granted";
    } catch {
      // Storage can be blocked; the question is then simply asked every time.
    }
    // The stored choice exists only in the browser, so it can only be read after mount.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setChoice(granted ? "granted" : "asked");
  }, []);

  function grant() {
    try {
      window.localStorage.setItem(MAP_CONSENT_KEY, "granted");
    } catch {
      // Granted for this visit all the same.
    }
    setChoice("granted");
  }

  if (choice === "granted") {
    return <>{children}</>;
  }
  if (choice === "unknown") {
    return <div className="absolute inset-0 animate-pulse bg-muted" />;
  }
  return (
    <div className="absolute inset-0 flex items-center justify-center bg-muted p-4">
      <div className="flex max-w-md flex-col items-start gap-3 rounded-xl border bg-card p-6 shadow-sm">
        <h2 className="text-lg font-semibold">{t.title}</h2>
        <p className="text-sm leading-relaxed text-muted-foreground">{t.body}</p>
        <div className="mt-1 flex flex-wrap items-center gap-4">
          <Button onClick={grant}>{t.button}</Button>
          <Link href={privacyHref} className="text-sm text-muted-foreground underline-offset-2 hover:underline">
            {t.privacyLink}
          </Link>
        </div>
      </div>
    </div>
  );
}

/** For the privacy page: takes the choice back, as easily as it was given. */
export function MapConsentWithdraw({ label, done }: { label: string; done: string }) {
  const [withdrawn, setWithdrawn] = useState(false);

  function withdraw() {
    try {
      window.localStorage.removeItem(MAP_CONSENT_KEY);
    } catch {
      // Nothing was stored, so there is nothing to take back.
    }
    setWithdrawn(true);
  }

  return withdrawn ? (
    <p role="status" className="font-medium text-foreground">
      {done}
    </p>
  ) : (
    <Button variant="outline" size="sm" onClick={withdraw} className="self-start">
      {label}
    </Button>
  );
}
