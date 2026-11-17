"use client";

import Link from "next/link";
import { useParams } from "next/navigation";

import { Button } from "@/components/ui/button";
import { getDictionary } from "@/lib/dictionaries";
import { DEFAULT_LOCALE, isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";

/** A not-found file receives no params, so the language comes from the address. */
export default function NotFound() {
  const params = useParams<{ locale?: string }>();
  const locale = params.locale && isLocale(params.locale) ? params.locale : DEFAULT_LOCALE;
  const t = getDictionary(locale);

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col items-start gap-4 px-4 py-24">
      <h1 className="text-3xl font-semibold tracking-tight">{t.notFound.title}</h1>
      <p className="text-muted-foreground">{t.notFound.body}</p>
      <Button asChild size="lg">
        <Link href={routes.feed(locale)}>{t.notFound.back}</Link>
      </Button>
    </main>
  );
}
