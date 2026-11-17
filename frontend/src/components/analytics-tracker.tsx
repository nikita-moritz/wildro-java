"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";

/**
 * One beacon per page view, on first load and on every client-side navigation
 * (SPEC.md §13). No cookie and no storage on the device: the visitor is
 * recognised on the server by a daily hash, which is why no consent is needed.
 *
 * The query string is read from window.location rather than useSearchParams,
 * which would demand a Suspense boundary and pull the pages out of static
 * rendering. By the time the effect runs, the URL is already the new one.
 */
export function AnalyticsTracker() {
  const pathname = usePathname();
  const lastSent = useRef<string | null>(null);

  useEffect(() => {
    // React runs effects twice in development; one view is one row.
    if (lastSent.current === pathname) {
      return;
    }
    lastSent.current = pathname;

    const params = new URLSearchParams(window.location.search);
    const body = JSON.stringify({
      path: pathname,
      referrer: document.referrer || null,
      utmSource: params.get("utm_source"),
      utmMedium: params.get("utm_medium"),
      utmCampaign: params.get("utm_campaign"),
    });

    if ("sendBeacon" in navigator) {
      navigator.sendBeacon("/api/track", new Blob([body], { type: "application/json" }));
      return;
    }
    fetch("/api/track", { method: "POST", headers: { "Content-Type": "application/json" }, body, keepalive: true }).catch(
      () => undefined,
    );
  }, [pathname]);

  return null;
}
