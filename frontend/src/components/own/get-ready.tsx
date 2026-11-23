import Link from "next/link";

import { Button } from "@/components/ui/button";
import type { Dictionary } from "@/lib/dictionaries";
import type { Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { TERMS_VERSION } from "@/lib/terms";

/**
 * What a first event or group needs before its form: a public name and the
 * accepted terms. Asked right here, each a plain form that comes back to `next`,
 * instead of a detour through the account page.
 */
export function GetReady({
  locale,
  t,
  next,
  needsName,
  needsTerms,
}: {
  locale: Locale;
  t: Dictionary;
  next: string;
  needsName: boolean;
  needsTerms: boolean;
}) {
  return (
    <section className="flex flex-col gap-5 rounded-2xl border bg-card p-5">
      <div className="flex flex-col gap-1">
        <h2 className="font-semibold">{t.own.setupTitle}</h2>
        <p className="text-sm text-muted-foreground">{t.own.setupLead}</p>
      </div>
      {needsName && (
        <form method="post" action="/api/account/display-name" className="flex flex-col gap-2">
          <input type="hidden" name="locale" value={locale} />
          <input type="hidden" name="next" value={next} />
          <label htmlFor="displayName" className="text-sm font-medium">
            {t.auth.displayName}
          </label>
          <div className="flex flex-col gap-2 sm:flex-row">
            <input
              id="displayName"
              name="displayName"
              required
              minLength={2}
              maxLength={50}
              autoComplete="nickname"
              className="h-11 flex-1 rounded-lg border bg-background px-3 text-base"
            />
            <Button type="submit" className="h-11">
              {t.auth.save}
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">{t.auth.displayNameHint}</p>
        </form>
      )}
      {needsTerms && (
        <form method="post" action="/api/account/terms" className={`flex flex-col gap-3 text-sm ${needsName ? "border-t pt-5" : ""}`}>
          <input type="hidden" name="locale" value={locale} />
          <input type="hidden" name="next" value={next} />
          {/* The version this page knows; the service refuses a mismatch rather than record consent to unseen text. */}
          <input type="hidden" name="version" value={TERMS_VERSION} />
          <span className="font-medium">{t.own.termsTitle}</span>
          <span className="text-muted-foreground">
            {t.own.termsNeeded}{" "}
            <Link href={routes.terms(locale)} target="_blank" className="text-primary underline-offset-2 hover:underline">
              {t.own.termsRead}
            </Link>
          </span>
          <Button type="submit" className="h-11 self-start">
            {t.own.termsAccept}
          </Button>
        </form>
      )}
    </section>
  );
}
