"use client";

import "mapbox-gl/dist/mapbox-gl.css";

import { Map as MapboxMap, NavigationControl, Popup, type GeoJSONSource, type LngLatLike } from "mapbox-gl";
import { CalendarDays, ChevronUp, MapPin, Search } from "lucide-react";
import { useTheme } from "next-themes";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { ACTIVITY_COLOR, ACTIVITY_ICON } from "@/components/activity";
import { defaultCover } from "@/lib/covers";
import type { PointsPage } from "@/lib/events-api";
import { formatShortDate, formatShortSpan, formatTime } from "@/lib/format";
import { MAP_STYLE } from "@/lib/map-style";
import type { Locale } from "@/lib/locales";
import { PriceBadge } from "@/components/price-badge";
import { cn } from "@/lib/utils";
import { ACTIVITY_TYPES, type ActivityType } from "@/lib/vocabulary";

/**
 * The map as a way to find an event: markers coloured by activity, clusters
 * with a count where markers would pile up, and beside it the list of what the
 * visible rectangle holds.
 *
 * The basemap is Mapbox's, owner's call on 2026-10-06: the visitor's browser
 * fetches the style, tiles and fonts from Mapbox and sends it usage events.
 * That is why this component is only ever mounted behind MapConsent, and why
 * the privacy policy has a section for it. Mapbox bills per initialisation of
 * a Map object, so nothing here creates one more often than it has to.
 */
export interface MapText {
  emptyView: string;
  tooMany: string;
  openEvent: string;
  /** Carries {count}. */
  inView: string;
  listLabel: string;
  search: string;
  noMatch: string;
  activityLabels: Record<ActivityType, string>;
  priceLabels: { free: string; paid: string };
}

type Point = PointsPage["items"][number];
type Status = "loading" | "ready" | "empty" | "truncated" | "error";

/** The Aachen region, where the first sources are, as the working version starts. */
const DEFAULT_VIEW = { center: [6.4, 50.55] as [number, number], zoom: 8.5 };

/** Pans come in bursts; ask once the map has settled. */
const DEBOUNCE_MS = 250;

/** Above this zoom nothing is clustered any more, so every marker can be reached. */
const CLUSTER_MAX_ZOOM = 13;

const SOURCE = "events";
const MARKER_SIZE = 36;

/** Outdoors shows trails, contours and hillshade, which is what these events are about. */

/** The brand green of each theme, as hex: the engine parses colours itself. */
const BRAND = { light: { fill: "#1b7f45", text: "#ffffff" }, dark: { fill: "#4ade80", text: "#052e16" } };

/** A round marker in the activity's colour with its icon in white, drawn once per activity. */
async function addMarkerImages(map: MapboxMap) {
  await Promise.all(
    ACTIVITY_TYPES.map(async (type) => {
      const Icon = ACTIVITY_ICON[type];
      const icon = renderToStaticMarkup(<Icon size={18} color="#ffffff" strokeWidth={2.25} />);
      const svg =
        `<svg xmlns="http://www.w3.org/2000/svg" width="${MARKER_SIZE * 2}" height="${MARKER_SIZE * 2}" viewBox="0 0 ${MARKER_SIZE} ${MARKER_SIZE}">` +
        `<circle cx="18" cy="18" r="15.5" fill="${ACTIVITY_COLOR[type]}" stroke="#ffffff" stroke-width="2.5"/>` +
        `<g transform="translate(9 9)">${icon}</g></svg>`;
      const image = new Image(MARKER_SIZE * 2, MARKER_SIZE * 2);
      image.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
      await image.decode();
      if (!map.hasImage(`activity-${type}`)) {
        map.addImage(`activity-${type}`, image, { pixelRatio: 2 });
      }
    }),
  );
}

function addEventLayers(map: MapboxMap, dark: boolean) {
  const brand = dark ? BRAND.dark : BRAND.light;
  map.addSource(SOURCE, {
    type: "geojson",
    data: { type: "FeatureCollection", features: [] },
    cluster: true,
    clusterRadius: 46,
    clusterMaxZoom: CLUSTER_MAX_ZOOM,
  });
  map.addLayer({
    id: "clusters",
    type: "circle",
    source: SOURCE,
    filter: ["has", "point_count"],
    paint: {
      "circle-color": brand.fill,
      "circle-radius": ["step", ["get", "point_count"], 18, 5, 22, 20, 27],
      "circle-stroke-color": brand.fill,
      "circle-stroke-opacity": 0.25,
      "circle-stroke-width": 6,
    },
  });
  map.addLayer({
    id: "cluster-count",
    type: "symbol",
    source: SOURCE,
    filter: ["has", "point_count"],
    layout: {
      "text-field": ["get", "point_count_abbreviated"],
      "text-font": ["DIN Pro Medium", "Arial Unicode MS Bold"],
      "text-size": 14,
      "text-allow-overlap": true,
    },
    paint: { "text-color": brand.text },
  });
  // The ring behind the marker whose row in the list is under the pointer.
  map.addLayer({
    id: "point-halo",
    type: "circle",
    source: SOURCE,
    filter: ["==", ["get", "slug"], ""],
    paint: { "circle-radius": 25, "circle-color": brand.fill, "circle-opacity": 0.3 },
  });
  map.addLayer({
    id: "points",
    type: "symbol",
    source: SOURCE,
    filter: ["!", ["has", "point_count"]],
    layout: {
      "icon-image": ["concat", "activity-", ["get", "activityType"]],
      "icon-allow-overlap": true,
    },
  });
}

function toFeatures(points: Point[]): GeoJSON.FeatureCollection<GeoJSON.Point, Point> {
  return {
    type: "FeatureCollection",
    features: points.map((point) => ({
      type: "Feature",
      geometry: { type: "Point", coordinates: [point.longitude, point.latitude] },
      properties: point,
    })),
  };
}

const samePlace = (a: Point, b: Point) =>
  a.latitude.toFixed(5) === b.latitude.toFixed(5) && a.longitude.toFixed(5) === b.longitude.toFixed(5);

const bySoonest = (a: Point, b: Point) => a.startsAt.localeCompare(b.startsAt);

export default function EventMap({
  locale,
  filterQuery,
  eventHref,
  accessToken,
  t,
  filters,
}: {
  locale: Locale;
  filterQuery: string;
  /** Where a marker leads, with {slug} as the placeholder; built from routes.ts on the server. */
  eventHref: string;
  /** Mapbox's public token, restricted to this site's addresses in the Mapbox account. */
  accessToken: string;
  t: MapText;
  /** The filter panel, rendered on the server and opened inside the list island. */
  filters?: React.ReactNode;
}) {
  const container = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapboxMap | null>(null);
  // Survives the rebuild a theme change causes, so the visitor stays where they were.
  const view = useRef(DEFAULT_VIEW);
  const [points, setPoints] = useState<Point[]>([]);
  const [status, setStatus] = useState<Status>("loading");
  const [hovered, setHovered] = useState<string | null>(null);
  // Mobile only: the list starts as a bar under the map so the map gets the screen.
  const [listOpen, setListOpen] = useState(false);
  const [query, setQuery] = useState("");
  // The filter is read at each load rather than rebuilding the map with it:
  // a rebuild blinks and is one more billed Mapbox load.
  const filterRef = useRef(filterQuery);
  const reloadRef = useRef<(() => void) | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const { resolvedTheme } = useTheme();
  const dark = resolvedTheme === "dark";

  const hrefOf = (slug: string) => eventHref.replace("{slug}", encodeURIComponent(slug));

  useEffect(() => {
    if (!container.current) {
      return;
    }
    const map = new MapboxMap({
      container: container.current,
      accessToken,
      style: dark ? MAP_STYLE.dark : MAP_STYLE.light,
      language: locale,
      // The globe is the default; at the zooms an event search happens on it only costs frames.
      projection: "mercator",
      center: view.current.center,
      zoom: view.current.zoom,
      minZoom: 4,
      maxZoom: 17,
      // The list island covers the bottom-left corner, where the logo sits by default.
      logoPosition: "bottom-right",
      dragRotate: false,
      pitchWithRotate: false,
      touchPitch: false,
    });
    mapRef.current = map;
    map.touchZoomRotate.disableRotation();
    map.addControl(new NavigationControl({ showCompass: false }), "bottom-right");

    const popup = new Popup({ offset: 20, maxWidth: "280px" });
    let timer: ReturnType<typeof setTimeout> | undefined;
    let inFlight: AbortController | undefined;

    /** {@code refresh}: a new filter, not a pan; the list dims until the answer is in. */
    async function load(refresh = false) {
      const bounds = map.getBounds();
      if (!bounds) {
        return;
      }
      inFlight?.abort();
      inFlight = new AbortController();
      if (refresh) {
        setRefreshing(true);
      }
      const params = new URLSearchParams(filterRef.current);
      params.set("locale", locale);
      params.set("south", bounds.getSouth().toFixed(4));
      params.set("west", bounds.getWest().toFixed(4));
      params.set("north", bounds.getNorth().toFixed(4));
      params.set("east", bounds.getEast().toFixed(4));
      try {
        const response = await fetch(`/api/points?${params}`, { signal: inFlight.signal });
        if (!response.ok) {
          setStatus("error");
          setRefreshing(false);
          return;
        }
        const page: PointsPage = await response.json();
        (map.getSource(SOURCE) as GeoJSONSource | undefined)?.setData(toFeatures(page.items));
        setPoints(page.items);
        setStatus(page.items.length === 0 ? "empty" : page.truncated ? "truncated" : "ready");
        setRefreshing(false);
      } catch (error) {
        // An aborted load was replaced by a newer one, which settles the state.
        if (!(error instanceof DOMException && error.name === "AbortError")) {
          setStatus("error");
          setRefreshing(false);
        }
      }
    }

    function schedule() {
      const center = map.getCenter();
      view.current = { center: [center.lng, center.lat], zoom: map.getZoom() };
      clearTimeout(timer);
      timer = setTimeout(load, DEBOUNCE_MS);
    }

    function open(at: LngLatLike, group: Point[]) {
      popup
        .setLngLat(at)
        .setDOMContent(popupContent([...group].sort(bySoonest), locale, hrefOf, t))
        .addTo(map);
    }

    function onPoint(event: { point: { x: number; y: number } }) {
      // Past the last cluster zoom, events sharing a start point lie on top of
      // each other; one click lists them all.
      const stacked = map.queryRenderedFeatures([event.point.x, event.point.y], { layers: ["points"] });
      const group = stacked.map((feature) => feature.properties as unknown as Point);
      const first = group[0];
      if (first) {
        open([first.longitude, first.latitude], group.filter((point) => samePlace(point, first)));
      }
    }

    function onCluster(event: { point: { x: number; y: number } }) {
      const cluster = map.queryRenderedFeatures([event.point.x, event.point.y], { layers: ["clusters"] })[0];
      const source = map.getSource(SOURCE) as GeoJSONSource | undefined;
      if (!cluster || !source || cluster.geometry.type !== "Point") {
        return;
      }
      const id = cluster.properties?.cluster_id as number;
      const at = cluster.geometry.coordinates as [number, number];
      source.getClusterLeaves(id, 50, 0, (error, found) => {
        const leaves = (found ?? []).map((leaf) => leaf.properties as unknown as Point);
        if (error || leaves.length === 0) {
          return;
        }
        // A cluster of events at one start point never comes apart by zooming.
        if (leaves.every((leaf) => samePlace(leaf, leaves[0]))) {
          open(at, leaves);
          return;
        }
        source.getClusterExpansionZoom(id, (zoomError, zoom) => {
          if (!zoomError && zoom != null) {
            map.easeTo({ center: at, zoom });
          }
        });
      });
    }

    const pointer = () => (map.getCanvas().style.cursor = "pointer");
    const arrow = () => (map.getCanvas().style.cursor = "");

    map.on("load", async () => {
      await addMarkerImages(map);
      addEventLayers(map, dark);
      map.on("click", "points", onPoint);
      map.on("click", "clusters", onCluster);
      for (const layer of ["points", "clusters"]) {
        map.on("mouseenter", layer, pointer);
        map.on("mouseleave", layer, arrow);
      }
      map.on("moveend", schedule);
      reloadRef.current = () => load(true);
      load();
    });

    return () => {
      clearTimeout(timer);
      inFlight?.abort();
      reloadRef.current = null;
      mapRef.current = null;
      map.remove();
    };
    // hrefOf and t are rebuilt every render from the props listed here.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [locale, eventHref, accessToken, dark, t.openEvent]);

  // A new filter reloads the markers of the same view; the map itself stays.
  // Before the map has loaded there is nothing to reload: its first load reads the ref.
  useEffect(() => {
    if (filterRef.current === filterQuery) {
      return;
    }
    filterRef.current = filterQuery;
    reloadRef.current?.();
  }, [filterQuery]);

  useEffect(() => {
    const map = mapRef.current;
    if (map?.getLayer("point-halo")) {
      map.setFilter("point-halo", ["==", ["get", "slug"], hovered ?? ""]);
    }
  }, [hovered]);

  const notice = status === "empty" ? t.emptyView : status === "truncated" ? t.tooMany : null;
  // Narrows what the visible rectangle already holds, as the visitor types; no request.
  const needle = query.trim().toLocaleLowerCase(locale);
  const shown = needle
    ? points.filter((point) => `${point.name} ${point.placeName}`.toLocaleLowerCase(locale).includes(needle))
    : points;
  const count = t.inView.replace("{count}", String(shown.length));

  return (
    // Desktop: the map fills the page and the list floats over its left edge as an island.
    // Mobile: the map on top and the list below it, so neither hides the map's controls.
    <div className="map-fade-in absolute inset-0 flex flex-col md:block">
      <div className="relative min-h-0 flex-1 md:absolute md:inset-0">
        {/* The engine's stylesheet positions its container itself, so the size comes from a wrapper. */}
        <div className="absolute inset-0">
          <div ref={container} className="h-full w-full" />
        </div>
      </div>

      <section
        aria-label={t.listLabel}
        className={cn(
          "z-10 flex flex-col overflow-hidden border-t bg-card",
          listOpen ? "max-h-[55%]" : "max-h-14",
          "md:absolute md:top-[4.5rem] md:bottom-14 md:left-3 md:max-h-none md:w-96 md:rounded-2xl md:border md:bg-card/95 md:shadow-xl md:backdrop-blur",
        )}
      >
        <button
          type="button"
          onClick={() => setListOpen((open) => !open)}
          aria-expanded={listOpen}
          className="flex shrink-0 items-center justify-between px-5 py-4 text-left md:pointer-events-none"
        >
          <span aria-live="polite" className="text-base font-semibold">
            {count}
          </span>
          <ChevronUp className={cn("h-5 w-5 text-muted-foreground transition-transform md:hidden", !listOpen && "rotate-180")} />
        </button>
        <div className={cn("min-h-0 flex-col overflow-y-auto", listOpen ? "flex" : "hidden", "md:flex")}>
          {/* Search and the filter button side by side; the open panel takes a row of its own and pushes the list down. */}
          <div className="flex flex-wrap items-center gap-2 px-3 pb-3">
            <label className="relative min-w-0 flex-1">
              <span className="sr-only">{t.search}</span>
              <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={t.search}
                className="h-10 w-full rounded-full border bg-background pr-3 pl-9 text-sm focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none"
              />
            </label>
            {filters}
          </div>
        <ul className={cn("flex flex-col gap-1 px-2 pb-2 transition-opacity duration-200", refreshing && "opacity-50")}>
          {shown.map((point) => (
            <li key={point.slug}>
              <Link
                href={hrefOf(point.slug)}
                onMouseEnter={() => setHovered(point.slug)}
                onMouseLeave={() => setHovered(null)}
                onFocus={() => setHovered(point.slug)}
                onBlur={() => setHovered(null)}
                className="flex gap-4 rounded-xl p-3 transition-colors hover:bg-muted focus-visible:bg-muted focus-visible:outline-none"
              >
                <Thumbnail point={point} />
                <span className="flex min-w-0 flex-col justify-center gap-1">
                  <span className="flex items-center gap-2">
                    <span className="text-xs font-medium" style={{ color: ACTIVITY_COLOR[point.activityType] }}>
                      {t.activityLabels[point.activityType]}
                    </span>
                    <PriceBadge amount={point.priceAmount} currency={point.priceCurrency} locale={locale} labels={t.priceLabels} />
                  </span>
                  <span className="line-clamp-2 text-sm leading-snug font-semibold">{point.name}</span>
                  <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    <CalendarDays className="h-3.5 w-3.5 shrink-0" />
                    <span className="truncate">{formatShortSpan(point.startsAt, point.endsAt, locale)}</span>
                  </span>
                  <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    <MapPin className="h-3.5 w-3.5 shrink-0" />
                    <span className="truncate">{point.placeName}</span>
                  </span>
                </span>
              </Link>
            </li>
          ))}
          {needle && shown.length === 0 && <li className="px-3 py-3 text-xs text-muted-foreground">{t.noMatch}</li>}
          {notice && <li className="px-3 py-3 text-xs text-muted-foreground">{notice}</li>}
        </ul>
        </div>
      </section>
    </div>
  );
}

/**
 * The event's own cover, else its activity's stand-in, else a tile in the
 * activity's colour. Small and lazy: the list can hold hundreds of rows.
 */
function Thumbnail({ point }: { point: Point }) {
  const standIn = defaultCover(point.activityType);
  const src = point.coverImageUrl ?? standIn?.small;
  if (!src) {
    const Icon = ACTIVITY_ICON[point.activityType];
    return (
      <span
        className="flex h-20 w-20 shrink-0 items-center justify-center rounded-xl text-white"
        style={{ backgroundColor: ACTIVITY_COLOR[point.activityType] }}
      >
        <Icon className="h-7 w-7" />
      </span>
    );
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt=""
      title={point.coverImageUrl ? (point.coverCredit ?? undefined) : undefined}
      loading="lazy"
      className="h-20 w-20 shrink-0 rounded-xl bg-muted object-cover"
    />
  );
}

/** Built with DOM calls, never an HTML string: event names come from outside. */
function popupContent(group: Point[], locale: Locale, hrefOf: (slug: string) => string, t: MapText): HTMLElement {
  const list = document.createElement("ul");
  list.className = "flex max-h-64 flex-col gap-3 overflow-y-auto text-sm";

  for (const point of group) {
    const item = document.createElement("li");
    item.className = "flex flex-col gap-1";

    const kind = document.createElement("span");
    kind.className = "flex items-center gap-1.5 text-xs font-medium";
    kind.style.color = ACTIVITY_COLOR[point.activityType];
    const dot = document.createElement("span");
    dot.className = "h-2 w-2 rounded-full";
    dot.style.backgroundColor = ACTIVITY_COLOR[point.activityType];
    kind.append(dot, t.activityLabels[point.activityType]);

    const name = document.createElement("strong");
    name.textContent = point.name;

    const when = document.createElement("span");
    when.className = "text-xs text-muted-foreground";
    when.textContent = `${formatShortDate(point.startsAt, locale)} · ${formatTime(point.startsAt, locale)}`;

    const link = document.createElement("a");
    link.href = hrefOf(point.slug);
    link.textContent = t.openEvent;
    link.className = "font-medium text-primary underline-offset-2 hover:underline";

    item.append(kind, name, when, link);
    list.append(item);
  }
  return list;
}
