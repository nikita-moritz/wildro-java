"use client";

import { Flag } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import type { Dictionary } from "@/lib/dictionaries";

const REASONS = ["SPAM", "MISLEADING", "DANGEROUS", "ILLEGAL", "PERSONAL_DATA", "OTHER"] as const;

/**
 * "Report event" (SPEC.md §18.2, DSA Art. 16): open to everyone, no account.
 * Folded away until asked for, so it costs the page nothing but a line. It
 * posts from the browser, which keeps the cached event page free of anything
 * per visitor.
 */
export function ReportForm({ slug, t }: { slug: string; t: Dictionary["report"] }) {
  const [state, setState] = useState<"idle" | "busy" | "sent" | "tooMany" | "failed">("idle");

  async function send(form: HTMLFormElement) {
    const data = new FormData(form);
    setState("busy");
    const response = await fetch("/api/reports", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ slug, reason: data.get("reason"), details: String(data.get("details") ?? "").trim() }),
    }).catch(() => null);
    setState(response?.ok ? "sent" : response?.status === 429 ? "tooMany" : "failed");
  }

  if (state === "sent") {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        {t.sent}
      </p>
    );
  }
  return (
    <details className="group text-sm">
      <summary className="flex w-fit cursor-pointer items-center gap-1.5 text-muted-foreground hover:text-foreground">
        <Flag className="h-3.5 w-3.5" />
        {t.open}
      </summary>
      <form
        className="mt-3 flex flex-col gap-3 rounded-xl border bg-card p-4"
        onSubmit={(event) => {
          event.preventDefault();
          void send(event.currentTarget);
        }}
      >
        <p className="font-medium">{t.title}</p>
        <label className="flex flex-col gap-1.5">
          {t.reason}
          <select name="reason" required className="h-11 rounded-lg border bg-background px-3 text-base">
            {REASONS.map((reason) => (
              <option key={reason} value={reason}>
                {t.reasons[reason]}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1.5">
          {t.details}
          <textarea name="details" required maxLength={2000} rows={3} className="rounded-lg border bg-background px-3 py-2 text-base" />
        </label>
        {(state === "tooMany" || state === "failed") && (
          <p role="alert" className="text-destructive">
            {state === "tooMany" ? t.tooMany : t.failed}
          </p>
        )}
        <Button type="submit" className="h-11 self-start" disabled={state === "busy"}>
          {t.send}
        </Button>
      </form>
    </details>
  );
}
