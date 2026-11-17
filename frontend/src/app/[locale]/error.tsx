"use client";

import { useParams } from "next/navigation";

import { Button } from "@/components/ui/button";
import { getDictionary } from "@/lib/dictionaries";
import { DEFAULT_LOCALE, isLocale } from "@/lib/locales";

/**
 * When the event service cannot be reached. Named as what it is and with a way
 * out, never a bare "something went wrong": the visitor should know it is worth
 * trying again.
 */
export default function ErrorBoundary({ retry }: { error: Error & { digest?: string }; retry: () => void }) {
  const params = useParams<{ locale?: string }>();
  const locale = params.locale && isLocale(params.locale) ? params.locale : DEFAULT_LOCALE;
  const t = getDictionary(locale);

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col items-start gap-4 px-4 py-24">
      <h1 className="text-3xl font-semibold tracking-tight">{t.error.title}</h1>
      <p className="text-muted-foreground">{t.error.body}</p>
      {/* retry, not reset: it fetches again, which is what a failed fetch needs. */}
      <Button size="lg" onClick={() => retry()}>
        {t.error.retry}
      </Button>
    </main>
  );
}
