"use client";

import dynamic from "next/dynamic";

/**
 * The map engine needs `window` and WebGL, so the map is loaded in the browser
 * only, and its weight stays out of every other page. `ssr: false` is
 * allowed only from a client component, hence this small wrapper.
 */
export const EventMapLoader = dynamic(() => import("@/components/event-map"), {
  ssr: false,
  loading: () => <div className="absolute inset-0 animate-pulse bg-muted" />,
});
