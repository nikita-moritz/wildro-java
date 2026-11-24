"use client";

import { Loader2, LocateFixed, SlidersHorizontal, X } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, useTransition } from "react";

import { DateField } from "@/components/date-field";
import { Button } from "@/components/ui/button";
import type { FeedFilter } from "@/lib/feed-filter";
import { EMPTY_FILTER, PRICE_CHOICES, RADIUS_OPTIONS_KM, filterToParams, roundCoordinate, type PriceChoice } from "@/lib/feed-filter";
import type { Locale } from "@/lib/locales";
import { ACTIVITY_TYPES, DIFFICULTIES, type ActivityType, type Difficulty } from "@/lib/vocabulary";
import { cn } from "@/lib/utils";

/**
 * The working version's Filters button and panel, on the list and on the map.
 *
 * It is a plain GET form inside a <details> element: opening it and applying a
 * filter need no script at all, and the result is an ordinary address. Only
 * "near me" needs JavaScript, because only the browser knows where the visitor
 * is, and it asks only after the visitor presses the button (SPEC.md §0.4).
 *
 * With JavaScript, Apply and Reset move to the new address in place instead of
 * reloading the page: the old results stay until the new ones arrive, so the
 * page does not blink, and the map is not rebuilt (a rebuild is a billed load).
 *
 * "Meine Gruppen" needs JavaScript too: which groups are the visitor's only the
 * session knows, so the panel asks the first time it is opened, and shows the
 * choice only to a member of at least one group (SPEC.md §18.8).
 */
export interface FiltersText {
  button: string;
  title: string;
  activity: string;
  difficulty: string;
  price: string;
  priceAny: string;
  groups: string;
  onlyMine: string;
  dates: string;
  dateFrom: string;
  dateTo: string;
  datePlaceholder: string;
  dateClear: string;
  distance: string;
  distanceMin: string;
  distanceMax: string;
  nearMe: string;
  nearMeHint: string;
  radius: string;
  locating: string;
  locationDenied: string;
  apply: string;
  reset: string;
  close: string;
  activityLabels: Record<ActivityType, string>;
  difficultyLabels: Record<Difficulty, string>;
  priceLabels: Record<PriceChoice, string>;
}

type Location = { state: "off" } | { state: "locating" } | { state: "denied" } | { state: "on"; lat: number; lon: number };

export function FiltersPanel({
  action,
  locale,
  filter,
  activeCount,
  t,
  allowNearMe = true,
  inline = false,
}: {
  action: string;
  locale: Locale;
  filter: FeedFilter;
  activeCount: number;
  t: FiltersText;
  allowNearMe?: boolean;
  /** Opens in place and pushes what follows down, instead of dropping over the page. */
  inline?: boolean;
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const details = useRef<HTMLDetailsElement>(null);
  const [location, setLocation] = useState<Location>(locationOf(filter));
  // The ids of the visitor's groups; null until asked.
  const [myGroups, setMyGroups] = useState<number[] | null>(null);

  function askMyGroups() {
    if (myGroups !== null || !details.current?.open) {
      return;
    }
    setMyGroups([]);
    fetch("/api/account/communities", { cache: "no-store" })
      .then((response) => (response.ok ? response.json() : { ids: [] }))
      .then((body: { ids?: unknown }) =>
        setMyGroups(Array.isArray(body.ids) ? body.ids.filter((id): id is number => Number.isSafeInteger(id)) : []),
      )
      .catch(() => {});
  }

  // The fields are uncontrolled and keep the values they were first given, so
  // the form is remounted whenever what it should show changes: a new filter
  // arrived with the page, or Reset was pressed and the fields empty at once,
  // before the new page is there. Without this, Reset left the old choices in
  // the fields and the next Apply brought them back.
  const appliedKey = filterToParams(filter).toString();
  const [applied, setApplied] = useState(appliedKey);
  const [shown, setShown] = useState(filter);
  const [resets, setResets] = useState(0);
  if (applied !== appliedKey) {
    setApplied(appliedKey);
    setShown(filter);
    setLocation(locationOf(filter));
  }

  // A filter that took effect closes the panel, so the results are what the visitor sees.
  useEffect(() => {
    if (details.current) {
      details.current.open = false;
    }
  }, [appliedKey]);

  function go(search: string) {
    startTransition(() => router.push(search ? `${action}?${search}` : action, { scroll: false }));
  }

  /**
   * A GET form sends every field, empty or not, which would leave
   * "&from=&to=&minKm=" in the address, so the address is built from the filled
   * fields only. Without JavaScript the form submits as it is, just less tidy.
   */
  function apply(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const params = new URLSearchParams();
    new FormData(event.currentTarget).forEach((value, key) => {
      if (typeof value === "string" && value !== "") {
        params.append(key, value);
      }
    });
    go(params.toString());
  }

  function reset(event: React.MouseEvent) {
    event.preventDefault();
    setShown(EMPTY_FILTER);
    setLocation({ state: "off" });
    setResets((count) => count + 1);
    go("");
  }

  function locate() {
    if (!("geolocation" in navigator)) {
      setLocation({ state: "denied" });
      return;
    }
    setLocation({ state: "locating" });
    navigator.geolocation.getCurrentPosition(
      (position) =>
        setLocation({
          state: "on",
          lat: roundCoordinate(position.coords.latitude),
          lon: roundCoordinate(position.coords.longitude),
        }),
      () => setLocation({ state: "denied" }),
      { maximumAge: 600_000, timeout: 10_000 },
    );
  }

  return (
    <details ref={details} onToggle={askMyGroups} className={cn("group", inline ? "contents" : "relative")}>
      <summary
        className={cn(
          // h-11: the height of the search box beside it on the events page.
          "flex h-11 cursor-pointer list-none items-center gap-1.5 rounded-lg border bg-card/90 px-3 text-sm font-medium shadow-sm backdrop-blur transition-colors hover:bg-muted [&::-webkit-details-marker]:hidden",
          inline && "h-10 shrink-0 rounded-full group-open:border-primary/40 group-open:bg-primary/10 group-open:text-primary",
        )}
      >
        <SlidersHorizontal className="h-4 w-4" />
        {t.button}
        {activeCount > 0 && (
          <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-xs font-semibold text-primary-foreground">
            {activeCount}
          </span>
        )}
      </summary>

      <form
        key={`${resets}:${filterToParams(shown).toString()}`}
        action={action}
        method="get"
        onSubmit={apply}
        aria-busy={pending}
        className={cn(
          "flex flex-col gap-5 rounded-xl border bg-card p-4",
          inline
            ? "order-last mt-3 basis-full"
            : "absolute top-full right-0 z-600 mt-2 max-h-[min(36rem,calc(100dvh-8rem))] w-[min(22rem,calc(100vw-1.5rem))] overflow-y-auto shadow-lg",
        )}
      >
        <div className="flex items-center justify-between">
          <p className="text-sm font-semibold">{t.title}</p>
          <Link href={action} onClick={reset} className="text-xs font-medium text-muted-foreground hover:text-foreground">
            {t.reset}
          </Link>
        </div>

        {/* The search box is its own form; this keeps its text when filters are applied. */}
        {shown.q && <input type="hidden" name="q" value={shown.q} />}

        {/* Chosen earlier, or offered now: a member of no group never sees it. */}
        {(shown.communities.length > 0 || (myGroups?.length ?? 0) > 0) && (
          <Group label={t.groups}>
            <MineChip chosen={shown.communities} mine={myGroups ?? []}>
              {t.onlyMine}
            </MineChip>
          </Group>
        )}

        <Group label={t.activity}>
          {ACTIVITY_TYPES.map((type) => (
            <Chip key={type} name="type" value={type} checked={shown.types.includes(type)}>
              {t.activityLabels[type]}
            </Chip>
          ))}
        </Group>

        <Group label={t.difficulty}>
          {DIFFICULTIES.map((difficulty) => (
            <Chip key={difficulty} name="difficulty" value={difficulty} checked={shown.difficulties.includes(difficulty)}>
              {t.difficultyLabels[difficulty]}
            </Chip>
          ))}
        </Group>

        <Group label={t.price}>
          <Chip type="radio" name="price" value="" checked={shown.price === null}>
            {t.priceAny}
          </Chip>
          {PRICE_CHOICES.map((choice) => (
            <Chip key={choice} type="radio" name="price" value={choice} checked={shown.price === choice}>
              {t.priceLabels[choice]}
            </Chip>
          ))}
        </Group>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-xs font-semibold tracking-widest text-muted-foreground uppercase">{t.dates}</legend>
          <div className="grid grid-cols-2 gap-2">
            <DateField
              label={t.dateFrom}
              name="from"
              defaultValue={shown.from ?? ""}
              locale={locale}
              placeholder={t.datePlaceholder}
              clearLabel={t.dateClear}
              className={INPUT}
            />
            <DateField
              label={t.dateTo}
              name="to"
              defaultValue={shown.to ?? ""}
              locale={locale}
              placeholder={t.datePlaceholder}
              clearLabel={t.dateClear}
              className={INPUT}
            />
          </div>
        </fieldset>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-xs font-semibold tracking-widest text-muted-foreground uppercase">{t.distance}</legend>
          <div className="grid grid-cols-2 gap-2">
            <Field label={t.distanceMin}>
              <input type="number" name="minKm" min={0} step={1} inputMode="numeric" defaultValue={shown.minKm ?? ""} className={INPUT} />
            </Field>
            <Field label={t.distanceMax}>
              <input type="number" name="maxKm" min={0} step={1} inputMode="numeric" defaultValue={shown.maxKm ?? ""} className={INPUT} />
            </Field>
          </div>
        </fieldset>

        {allowNearMe && (
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-xs font-semibold tracking-widest text-muted-foreground uppercase">{t.nearMe}</legend>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant={location.state === "on" ? "default" : "outline"}
                size="sm"
                onClick={location.state === "on" ? () => setLocation({ state: "off" }) : locate}
                className="gap-1.5"
              >
                <LocateFixed className="h-4 w-4" />
                {location.state === "locating" ? t.locating : t.nearMe}
                {location.state === "on" && <X className="h-3.5 w-3.5" />}
              </Button>
              {location.state === "on" && (
                <label className="flex items-center gap-2 text-xs text-muted-foreground">
                  {t.radius}
                  <select name="radiusKm" defaultValue={shown.near?.radiusKm ?? 25} className={cn(INPUT, "w-auto")}>
                    {RADIUS_OPTIONS_KM.map((km) => (
                      <option key={km} value={km}>
                        {km} km
                      </option>
                    ))}
                  </select>
                </label>
              )}
            </div>
            {location.state === "on" && (
              <>
                <input type="hidden" name="lat" value={location.lat} />
                <input type="hidden" name="lon" value={location.lon} />
              </>
            )}
            <p className={cn("text-xs", location.state === "denied" ? "text-destructive" : "text-muted-foreground")}>
              {location.state === "denied" ? t.locationDenied : t.nearMeHint}
            </p>
          </fieldset>
        )}

        <Button type="submit" size="touch" className="w-full" disabled={pending}>
          {pending && <Loader2 className="h-4 w-4 animate-spin" />}
          {t.apply}
        </Button>
      </form>
    </details>
  );
}

const CHIP =
  "inline-flex min-h-8 items-center rounded-full border px-3 text-xs font-medium text-muted-foreground transition-colors peer-checked:border-primary/40 peer-checked:bg-primary/10 peer-checked:text-primary peer-focus-visible:ring-2 peer-focus-visible:ring-ring/50 hover:bg-muted";

const INPUT =
  "h-9 w-full rounded-md border bg-background px-2 text-sm focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none";

function locationOf(filter: FeedFilter): Location {
  return filter.near ? { state: "on", lat: filter.near.lat, lon: filter.near.lon } : { state: "off" };
}

function Group({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <fieldset>
      <legend className="mb-2 text-xs font-semibold tracking-widest text-muted-foreground uppercase">{label}</legend>
      <div className="flex flex-wrap gap-1.5">{children}</div>
    </fieldset>
  );
}

/** A checkbox, or a radio for a one-of choice, drawn as the working version's filter chip. */
function Chip({
  name,
  value,
  checked,
  type = "checkbox",
  children,
}: {
  name: string;
  value: string;
  checked: boolean;
  type?: "checkbox" | "radio";
  children: React.ReactNode;
}) {
  return (
    <label className="cursor-pointer">
      <input type={type} name={name} value={value} defaultChecked={checked} className="peer sr-only" />
      <span className={CHIP}>{children}</span>
    </label>
  );
}

/**
 * One choice that stands for several values: on, it sends one "community" per
 * group. Its own groups when known, else those already in the address.
 */
function MineChip({ chosen, mine, children }: { chosen: number[]; mine: number[]; children: React.ReactNode }) {
  const [on, setOn] = useState(chosen.length > 0);
  const ids = mine.length > 0 ? mine : chosen;
  return (
    <label className="cursor-pointer">
      <input type="checkbox" checked={on} onChange={(event) => setOn(event.target.checked)} className="peer sr-only" />
      <span className={CHIP}>{children}</span>
      {on && ids.map((id) => <input key={id} type="hidden" name="community" value={id} />)}
    </label>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1 text-xs text-muted-foreground">
      {label}
      {children}
    </label>
  );
}
