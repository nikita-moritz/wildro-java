"use client";

import { useState } from "react";

import { LocationPicker } from "@/components/own/location-picker";
import { Button } from "@/components/ui/button";
import { DateTimeField } from "@/components/date-time-field";
import { berlinToIso, isoToBerlin } from "@/lib/berlin-time";
import type { Dictionary } from "@/lib/dictionaries";
import { LOCALES, type Locale } from "@/lib/locales";
import { MAX_UPLOAD } from "@/lib/upload-limit";
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

/** An event to start a new one from: every field but the dates, its owner and its picture. */
export interface EditorCopy {
  from: EditorInitial;
  communitySlug: string | null;
  coverImageUrl: string | null;
}

type Text = Pick<Dictionary, "own" | "activity" | "difficulty" | "filters">;

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
  copyOf = null,
}: {
  locale: Locale;
  initial: EditorInitial | null;
  /** On a new event only: the event it duplicates. */
  copyOf?: EditorCopy | null;
  t: Text;
  mapboxToken: string | null;
  /** Where to go after saving: the edit page, with {slug} filled in. */
  editPath: string;
  /** Communities the person may write for, offered on a new event only: ownership is fixed once created. */
  communities?: { slug: string; name: string }[];
}) {
  const own = t.own;
  // What the fields start from: the event being edited, or the one being duplicated.
  const base = initial ?? copyOf?.from ?? null;
  const [textLocale, setTextLocale] = useState<Locale>(base?.locale ?? locale);
  const [latitude, setLatitude] = useState(base ? String(base.latitude) : "");
  const [longitude, setLongitude] = useState(base ? String(base.longitude) : "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // A local preview of the chosen picture; nothing leaves the browser before saving.
  const [preview, setPreview] = useState<string | null>(null);
  // A duplicate takes the original's own picture along unless told not to or given another.
  const copiedCover = initial ? null : (copyOf?.coverImageUrl ?? null);
  const [keepCover, setKeepCover] = useState(true);
  const ownerDefault = communities.some((each) => each.slug === copyOf?.communitySlug) ? copyOf!.communitySlug! : "";
  // A copy keeps the time of day; the day itself is the one thing to choose anew.
  const copiedTime = (iso: string | null) => (copyOf && iso ? isoToBerlin(iso).slice(11, 16) : undefined);

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
      // A picture chosen while creating goes up right after: the event exists now.
      // Should it fail, the event stays and the edit page says why.
      let coverError = "";
      const cover = data.get("cover");
      const chosen = !initial && cover instanceof File && cover.size > 0;
      if (chosen && cover.size > MAX_UPLOAD) {
        coverError = "&error=TOO_LARGE";
      } else if (chosen || (copiedCover && keepCover)) {
        const upload = new FormData();
        upload.set("locale", locale);
        upload.set("reply", "json");
        if (chosen) {
          upload.set("intent", "upload");
          upload.set("cover", cover);
        } else {
          upload.set("intent", "copy");
          upload.set("from", copyOf!.from.slug);
        }
        const answer = await fetch(`/api/own/events/${encodeURIComponent(json.slug)}/cover`, { method: "POST", body: upload }).catch(() => null);
        if (!answer?.ok) {
          const refusal = (await answer?.json().catch(() => null)) as { code?: string } | null;
          coverError = `&error=${encodeURIComponent(refusal?.code ?? "UNAVAILABLE")}`;
        }
      }
      // A full navigation on purpose: the edit page reads the session and the fresh
      // event on the server, and a soft one could reuse a render made before the save.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.assign(`${editPath.replace("{slug}", json.slug)}?done=saved${coverError}`);
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
          <select name="communitySlug" defaultValue={ownerDefault} className={FIELD}>
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
        <input name="name" required maxLength={120} defaultValue={base?.name} className={FIELD} />
      </label>

      <label className={LABEL}>
        {own.fieldDescription}
        <textarea
          name="description"
          required
          maxLength={5000}
          rows={7}
          defaultValue={base?.description}
          className="w-full rounded-lg border bg-background px-3 py-2 text-base"
        />
      </label>

      {/* Only while creating: an existing event has its own picture section above the form. */}
      {!initial && (
        <div className={LABEL}>
          <span>{own.coverTitle}</span>
          {(preview ?? (keepCover ? copiedCover : null)) && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={preview ?? copiedCover!} alt="" className="aspect-[16/9] w-full rounded-xl border object-cover sm:w-80" />
          )}
          {copiedCover && !preview && (
            <label className="flex min-h-11 items-center gap-2 font-normal">
              <input type="checkbox" checked={keepCover} onChange={(e) => setKeepCover(e.target.checked)} className="size-4" />
              {own.coverKeep}
            </label>
          )}
          <label className="flex min-h-11 w-full cursor-pointer items-center rounded-lg border bg-background px-3 text-sm font-normal">
            <span className="sr-only">{own.coverChoose}</span>
            <input
              type="file"
              name="cover"
              accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
              onChange={(event) => {
                const file = event.target.files?.[0];
                setPreview((old) => {
                  if (old) {
                    URL.revokeObjectURL(old);
                  }
                  return file ? URL.createObjectURL(file) : null;
                });
              }}
              className="w-full text-sm file:mr-3 file:rounded-full file:border-0 file:bg-muted file:px-3 file:py-1.5 file:text-sm file:font-medium"
            />
          </label>
          <span className="text-xs font-normal text-muted-foreground">{own.coverOptional}</span>
        </div>
      )}

      <div className="grid gap-5 sm:grid-cols-2">
        <label className={LABEL}>
          {own.fieldActivity}
          <select name="activityType" required defaultValue={base?.activityType ?? "HIKING"} className={FIELD}>
            {ACTIVITY_TYPES.map((type) => (
              <option key={type} value={type}>
                {t.activity[type]}
              </option>
            ))}
          </select>
        </label>
        <label className={LABEL}>
          {own.fieldDifficulty}
          <select name="difficulty" defaultValue={base?.difficulty ?? ""} className={FIELD}>
            <option value="">{own.difficultyNone}</option>
            {DIFFICULTIES.map((difficulty) => (
              <option key={difficulty} value={difficulty}>
                {t.difficulty[difficulty]}
              </option>
            ))}
          </select>
        </label>
        <DateTimeField
          name="startsAt"
          label={own.fieldStarts}
          timeLabel={own.fieldTime}
          required
          defaultValue={isoToBerlin(initial?.startsAt ?? null)}
          defaultTime={copiedTime(copyOf?.from.startsAt ?? null)}
          locale={locale}
          placeholder={t.filters.datePlaceholder}
          clearLabel={t.filters.dateClear}
          className={FIELD}
        />
        <DateTimeField
          name="endsAt"
          label={own.fieldEnds}
          timeLabel={own.fieldTime}
          defaultValue={isoToBerlin(initial?.endsAt ?? null)}
          defaultTime={copiedTime(copyOf?.from.endsAt ?? null)}
          locale={locale}
          placeholder={t.filters.datePlaceholder}
          clearLabel={t.filters.dateClear}
          className={FIELD}
        />
      </div>

      <fieldset className="flex flex-col gap-3 rounded-xl border p-4">
        <legend className="px-1 text-sm font-medium">{own.fieldLocation}</legend>
        <label className={LABEL}>
          {own.fieldPlace}
          <input name="placeName" required maxLength={120} defaultValue={base?.placeName} className={FIELD} />
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
          <input name="distanceKm" type="number" step="0.1" min={0.1} defaultValue={base?.distanceKm ?? ""} className={FIELD} />
        </label>
        <label className={LABEL}>
          {own.fieldElevation}
          <input name="elevationGainM" type="number" step="1" min={0} defaultValue={base?.elevationGainM ?? ""} className={FIELD} />
        </label>
        <label className={LABEL}>
          {own.fieldCapacity}
          <input name="maxParticipants" type="number" step="1" min={1} defaultValue={base?.maxParticipants ?? ""} className={FIELD} />
        </label>
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <label className={LABEL}>
          {own.fieldEventLinks}
          <textarea name="eventLinks" rows={3} defaultValue={base?.eventLinks.join("\n")} className="w-full rounded-lg border bg-background px-3 py-2 text-base" />
          <span className="text-xs font-normal text-muted-foreground">{own.linksHint}</span>
        </label>
        <label className={LABEL}>
          {own.fieldCommunityLinks}
          <textarea
            name="communityLinks"
            rows={3}
            defaultValue={base?.communityLinks.join("\n")}
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
