"use client";

import { useState } from "react";

import { LocationPicker } from "@/components/own/location-picker";
import { Button } from "@/components/ui/button";
import { berlinToIso, isoToBerlin } from "@/lib/berlin-time";
import type { Dictionary } from "@/lib/dictionaries";
import { LOCALES, type Locale } from "@/lib/locales";
import { ACTIVITY_TYPES, DIFFICULTIES, type ActivityType, type Difficulty } from "@/lib/vocabulary";

/** What the editor starts from: an existing event, or nothing. */
export interface EditorInitial {
  slug: string;
  locale: Locale;
  name: string;
  placeName: string;
  description: string;
  activityType: ActivityType;
  difficulty: Difficulty | null;
  startsAt: string;
  endsAt: string | null;
  latitude: number;
  longitude: number;
  distanceKm: number | null;
  elevationGainM: number | null;
  maxParticipants: number | null;
  eventLinks: string[];
  communityLinks: string[];
}

type Text = Pick<Dictionary, "own" | "activity" | "difficulty">;

const FIELD = "h-11 w-full rounded-lg border bg-background px-3 text-base";
const LABEL = "flex flex-col gap-1.5 text-sm font-medium";

/**
 * The form for an own event. Sends JSON to this site's /api/own/events, which
 * passes it on with the person's token; every rule is checked by the event
 * service, this form only helps fill it in. A refusal keeps what was typed and
 * says what to fix.
 */
export function EventEditor({
  locale,
  initial,
  t,
  mapboxToken,
  editPath,
  communities = [],
}: {
  locale: Locale;
  initial: EditorInitial | null;
  t: Text;
  mapboxToken: string | null;
  /** Where to go after saving: the edit page, with {slug} filled in. */
  editPath: string;
  /** Communities the person may write for, offered on a new event only: ownership is fixed once created. */
  communities?: { slug: string; name: string }[];
}) {
  const own = t.own;
  const [textLocale, setTextLocale] = useState<Locale>(initial?.locale ?? locale);
  const [latitude, setLatitude] = useState(initial ? String(initial.latitude) : "");
  const [longitude, setLongitude] = useState(initial ? String(initial.longitude) : "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function useMyLocation() {
    navigator.geolocation?.getCurrentPosition((position) => {
      setLatitude(String(Math.round(position.coords.latitude * 1e6) / 1e6));
      setLongitude(String(Math.round(position.coords.longitude * 1e6) / 1e6));
    });
  }

  async function submit(form: HTMLFormElement, publish: boolean) {
    if (!form.reportValidity()) {
      return;
    }
    const data = new FormData(form);
    const value = (name: string) => String(data.get(name) ?? "").trim();
    const number = (name: string) => (value(name) === "" ? null : Number(value(name)));
    const lines = (name: string) => value(name).split(/\s+/).filter(Boolean);
    const event = {
      locale: textLocale,
      name: value("name"),
      placeName: value("placeName"),
      description: value("description"),
      activityType: value("activityType"),
      difficulty: value("difficulty") || null,
      startsAt: berlinToIso(value("startsAt")),
      endsAt: berlinToIso(value("endsAt")),
      latitude: number("latitude"),
      longitude: number("longitude"),
      distanceKm: number("distanceKm"),
      elevationGainM: number("elevationGainM"),
      maxParticipants: number("maxParticipants"),
      eventLinks: lines("eventLinks"),
      communityLinks: lines("communityLinks"),
      communitySlug: value("communitySlug") || null,
    };
    setBusy(true);
    setError(null);
    const response = await fetch(initial ? `/api/own/events/${encodeURIComponent(initial.slug)}` : "/api/own/events", {
      method: initial ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(initial ? { event } : { event, publish }),
    }).catch(() => null);
    const json = (await response?.json().catch(() => null)) as { slug?: string; code?: string; field?: string } | null;
    if (response?.ok && json?.slug) {
      // A full navigation on purpose: the edit page reads the session and the fresh
      // event on the server, and a soft one could reuse a render made before the save.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.assign(`${editPath.replace("{slug}", json.slug)}?done=saved`);
      return;
    }
    setBusy(false);
    const code = (json?.code ?? "GENERIC") as keyof typeof own.errors;
    const message = own.errors[code] ?? own.errors.GENERIC;
    setError(json?.field ? `${message} (${json.field})` : message);
  }

  return (
    <form
      className="flex flex-col gap-5"
      onSubmit={(event) => {
        event.preventDefault();
        void submit(event.currentTarget, false);
      }}
    >
      {!initial && communities.length > 0 && (
        <label className={LABEL}>
          {own.ownerLabel}
          <select name="communitySlug" defaultValue="" className={FIELD}>
            <option value="">{own.ownerMe}</option>
            {communities.map((community) => (
              <option key={community.slug} value={community.slug}>
                {community.name}
              </option>
            ))}
          </select>
        </label>
      )}

      <label className={LABEL}>
        {own.fieldLanguage}
        <select value={textLocale} onChange={(e) => setTextLocale(e.target.value as Locale)} className={FIELD}>
          {LOCALES.map((each) => (
            <option key={each} value={each}>
              {each.toUpperCase()}
            </option>
          ))}
        </select>
        <span className="text-xs font-normal text-muted-foreground">{own.fieldLanguageHint}</span>
      </label>

      <label className={LABEL}>
        {own.fieldName}
        <input name="name" required maxLength={120} defaultValue={initial?.name} className={FIELD} />
      </label>

      <label className={LABEL}>
        {own.fieldDescription}
        <textarea
          name="description"
          required
          maxLength={5000}
          rows={7}
          defaultValue={initial?.description}
          className="w-full rounded-lg border bg-background px-3 py-2 text-base"
        />
      </label>

      <div className="grid gap-5 sm:grid-cols-2">
        <label className={LABEL}>
          {own.fieldActivity}
          <select name="activityType" required defaultValue={initial?.activityType ?? "HIKING"} className={FIELD}>
            {ACTIVITY_TYPES.map((type) => (
              <option key={type} value={type}>
                {t.activity[type]}
              </option>
            ))}
          </select>
        </label>
        <label className={LABEL}>
          {own.fieldDifficulty}
          <select name="difficulty" defaultValue={initial?.difficulty ?? ""} className={FIELD}>
            <option value="">{own.difficultyNone}</option>
            {DIFFICULTIES.map((difficulty) => (
              <option key={difficulty} value={difficulty}>
                {t.difficulty[difficulty]}
              </option>
            ))}
          </select>
        </label>
        <label className={LABEL}>
          {own.fieldStarts}
          <input
            name="startsAt"
            type="datetime-local"
            required
            defaultValue={isoToBerlin(initial?.startsAt ?? null)}
            className={FIELD}
          />
        </label>
        <label className={LABEL}>
          {own.fieldEnds}
          <input name="endsAt" type="datetime-local" defaultValue={isoToBerlin(initial?.endsAt ?? null)} className={FIELD} />
        </label>
      </div>

      <fieldset className="flex flex-col gap-3 rounded-xl border p-4">
        <legend className="px-1 text-sm font-medium">{own.fieldLocation}</legend>
        <label className={LABEL}>
          {own.fieldPlace}
          <input name="placeName" required maxLength={120} defaultValue={initial?.placeName} className={FIELD} />
        </label>
        <p className="text-xs text-muted-foreground">{own.fieldLocationHint}</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className={LABEL}>
            {own.latitude}
            <input
              name="latitude"
              type="number"
              step="any"
              min={-90}
              max={90}
              required
              value={latitude}
              onChange={(e) => setLatitude(e.target.value)}
              className={FIELD}
            />
          </label>
          <label className={LABEL}>
            {own.longitude}
            <input
              name="longitude"
              type="number"
              step="any"
              min={-180}
              max={180}
              required
              value={longitude}
              onChange={(e) => setLongitude(e.target.value)}
              className={FIELD}
            />
          </label>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" className="h-11" onClick={useMyLocation}>
            {own.useMyLocation}
          </Button>
        </div>
        {mapboxToken && (
          <LocationPicker
            accessToken={mapboxToken}
            latitude={latitude === "" ? null : Number(latitude)}
            longitude={longitude === "" ? null : Number(longitude)}
            onPick={(lat, lng) => {
              setLatitude(String(lat));
              setLongitude(String(lng));
            }}
            label={own.pickOnMap}
          />
        )}
      </fieldset>

      <div className="grid gap-5 sm:grid-cols-3">
        <label className={LABEL}>
          {own.fieldDistance}
          <input name="distanceKm" type="number" step="0.1" min={0.1} defaultValue={initial?.distanceKm ?? ""} className={FIELD} />
        </label>
        <label className={LABEL}>
          {own.fieldElevation}
          <input name="elevationGainM" type="number" step="1" min={0} defaultValue={initial?.elevationGainM ?? ""} className={FIELD} />
        </label>
        <label className={LABEL}>
          {own.fieldCapacity}
          <input name="maxParticipants" type="number" step="1" min={1} defaultValue={initial?.maxParticipants ?? ""} className={FIELD} />
        </label>
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <label className={LABEL}>
          {own.fieldEventLinks}
          <textarea name="eventLinks" rows={3} defaultValue={initial?.eventLinks.join("\n")} className="w-full rounded-lg border bg-background px-3 py-2 text-base" />
          <span className="text-xs font-normal text-muted-foreground">{own.linksHint}</span>
        </label>
        <label className={LABEL}>
          {own.fieldCommunityLinks}
          <textarea
            name="communityLinks"
            rows={3}
            defaultValue={initial?.communityLinks.join("\n")}
            className="w-full rounded-lg border bg-background px-3 py-2 text-base"
          />
        </label>
      </div>

      {error && (
        <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        {initial ? (
          <Button type="submit" className="h-11" disabled={busy}>
            {own.save}
          </Button>
        ) : (
          <>
            <Button type="submit" variant="outline" className="h-11" disabled={busy}>
              {own.saveDraft}
            </Button>
            <Button
              type="button"
              className="h-11"
              disabled={busy}
              onClick={(event) => void submit(event.currentTarget.form!, true)}
            >
              {own.saveAndPublish}
            </Button>
          </>
        )}
      </div>
    </form>
  );
}
