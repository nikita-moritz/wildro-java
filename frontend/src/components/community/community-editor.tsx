"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import type { Dictionary } from "@/lib/dictionaries";
import { LOCALES, type Locale } from "@/lib/locales";

export interface CommunityInitial {
  slug: string;
  name: string;
  description: string;
  area: string;
  locale: Locale;
  links: string[];
}

const FIELD = "h-11 w-full rounded-lg border bg-background px-3 text-base";
const LABEL = "flex flex-col gap-1.5 text-sm font-medium";

/** Create or edit a community. Every rule is the community service's; a refusal keeps what was typed. */
export function CommunityEditor({
  locale,
  initial,
  t,
  managePath,
}: {
  locale: Locale;
  initial: CommunityInitial | null;
  t: Dictionary["community"];
  /** Where to go after saving, with {slug} filled in. */
  managePath: string;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(form: HTMLFormElement) {
    const data = new FormData(form);
    const value = (name: string) => String(data.get(name) ?? "").trim();
    const community = {
      name: value("name"),
      description: value("description"),
      area: value("area"),
      locale: value("locale"),
      links: value("links").split(/\s+/).filter(Boolean),
    };
    setBusy(true);
    setError(null);
    const response = await fetch(initial ? `/api/communities/${encodeURIComponent(initial.slug)}` : "/api/communities", {
      method: initial ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ community }),
    }).catch(() => null);
    const json = (await response?.json().catch(() => null)) as { slug?: string; code?: string } | null;
    if (response?.ok && json?.slug) {
      // A full navigation: the management page reads the session and the fresh community on the server.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.assign(`${managePath.replace("{slug}", json.slug)}?done=1`);
      return;
    }
    setBusy(false);
    const code = (json?.code ?? "GENERIC") as keyof typeof t.errors;
    setError(t.errors[code] ?? t.errors.GENERIC);
  }

  return (
    <form
      className="flex flex-col gap-5"
      onSubmit={(event) => {
        event.preventDefault();
        void submit(event.currentTarget);
      }}
    >
      <label className={LABEL}>
        {t.fieldName}
        <input name="name" required minLength={2} maxLength={80} defaultValue={initial?.name} className={FIELD} />
      </label>
      <label className={LABEL}>
        {t.fieldArea}
        <input name="area" required minLength={2} maxLength={120} defaultValue={initial?.area} className={FIELD} />
      </label>
      <label className={LABEL}>
        {t.fieldLanguage}
        <select name="locale" defaultValue={initial?.locale ?? locale} className={FIELD}>
          {LOCALES.map((each) => (
            <option key={each} value={each}>
              {each.toUpperCase()}
            </option>
          ))}
        </select>
      </label>
      <label className={LABEL}>
        {t.fieldDescription}
        <textarea name="description" required maxLength={5000} rows={6} defaultValue={initial?.description} className="w-full rounded-lg border bg-background px-3 py-2 text-base" />
      </label>
      <label className={LABEL}>
        {t.fieldLinks}
        <textarea name="links" rows={3} defaultValue={initial?.links.join("\n")} className="w-full rounded-lg border bg-background px-3 py-2 text-base" />
        <span className="text-xs font-normal text-muted-foreground">{t.linksHint}</span>
      </label>
      {error && (
        <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </p>
      )}
      <Button type="submit" className="h-11 self-start" disabled={busy}>
        {initial ? t.save : t.create}
      </Button>
    </form>
  );
}
