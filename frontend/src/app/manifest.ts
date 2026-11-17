import type { MetadataRoute } from "next";

import { brand } from "@/lib/env";

/**
 * Web app manifest: makes the site installable ("Add to Home Screen"), opening
 * in its own window with the brand colour. No service worker on purpose, as in
 * evolution-space: installing no longer needs one, and an offline cache would
 * write to the visitor's device, which the privacy policy does not list.
 * Icons are drawn from public/logo.svg into public/icons.
 */
export default function manifest(): MetadataRoute.Manifest {
  const { name, icons } = brand();
  return {
    name,
    short_name: name,
    description: "Outdoor events around Aachen: hikes, ranger tours, trail runs. Wanderungen und Outdoor-Events rund um Aachen.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#0d874a",
    icons: [
      { src: `${icons}/icon-192.png`, sizes: "192x192", type: "image/png" },
      { src: `${icons}/icon-512.png`, sizes: "512x512", type: "image/png" },
      { src: `${icons}/maskable-512.png`, sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
