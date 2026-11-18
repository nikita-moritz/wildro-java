"use client";

import "mapbox-gl/dist/mapbox-gl.css";

import type { Map as MapboxMap, Marker } from "mapbox-gl";
import { useTheme } from "next-themes";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { MAP_STYLE } from "@/lib/map-style";

/**
 * Picks the starting point by a click on the map. Mapbox loads only after the
 * button is pressed, as on the map page: before that no request leaves for it
 * (Datenschutz, section 5). Coordinates can always be typed instead.
 */
export function LocationPicker({
  accessToken,
  latitude,
  longitude,
  onPick,
  label,
}: {
  accessToken: string;
  latitude: number | null;
  longitude: number | null;
  onPick: (latitude: number, longitude: number) => void;
  label: string;
}) {
  const [open, setOpen] = useState(false);
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<MapboxMap | null>(null);
  const marker = useRef<Marker | null>(null);
  const { resolvedTheme } = useTheme();
  // The latest callback, read by the map's click handler, which is set up once.
  const pick = useRef(onPick);
  useEffect(() => {
    pick.current = onPick;
  });

  useEffect(() => {
    if (!open || !container.current || map.current) {
      return;
    }
    let cancelled = false;
    void import("mapbox-gl").then(({ Map, Marker, NavigationControl }) => {
      if (cancelled || !container.current) {
        return;
      }
      const start: [number, number] = longitude !== null && latitude !== null ? [longitude, latitude] : [6.0839, 50.7753];
      const created = new Map({
        container: container.current,
        accessToken,
        style: resolvedTheme === "dark" ? MAP_STYLE.dark : MAP_STYLE.light,
        center: start,
        zoom: latitude !== null ? 12 : 8,
      });
      created.addControl(new NavigationControl({ showCompass: false }));
      if (latitude !== null && longitude !== null) {
        marker.current = new Marker({ color: "#0d874a" }).setLngLat(start).addTo(created);
      }
      created.on("click", (event) => {
        const lat = Math.round(event.lngLat.lat * 1e6) / 1e6;
        const lng = Math.round(event.lngLat.lng * 1e6) / 1e6;
        marker.current?.remove();
        marker.current = new Marker({ color: "#0d874a" }).setLngLat([lng, lat]).addTo(created);
        pick.current(lat, lng);
      });
      map.current = created;
    });
    return () => {
      cancelled = true;
    };
  }, [open, accessToken, latitude, longitude, resolvedTheme]);

  useEffect(() => () => map.current?.remove(), []);

  if (!open) {
    return (
      <Button type="button" variant="outline" className="h-11 self-start" onClick={() => setOpen(true)}>
        {label}
      </Button>
    );
  }
  return <div ref={container} className="h-72 w-full overflow-hidden rounded-xl border" />;
}
