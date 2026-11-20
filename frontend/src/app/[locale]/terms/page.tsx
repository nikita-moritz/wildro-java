import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { SiteFooter } from "@/components/site-footer";
import { getDictionary } from "@/lib/dictionaries";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { alternatesFor } from "@/lib/seo";
import { TERMS, TERMS_AS_OF } from "@/lib/terms";

export async function generateMetadata({ params }: PageProps<"/[locale]/terms">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return {
    title: TERMS[locale].title,
    alternates: alternatesFor(routes.terms, locale),
    robots: { index: false, follow: true },
  };
}

/** The platform terms (SPEC.md §18.5): German binding, English and Russian translated. */
export default async function TermsPage({ params }: PageProps<"/[locale]/terms">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const terms = TERMS[locale];
  return (
    <>
      <main className="flex-1">
        <div className="mx-auto flex max-w-2xl flex-col gap-6 px-4 py-12">
          <BackLink href={routes.home(locale)} label={t.nav.back} className="-mb-4" />
          <h1 className="text-3xl font-semibold tracking-tight">{terms.title}</h1>
          <p className="text-sm text-muted-foreground">Stand: {TERMS_AS_OF}</p>
          {terms.note && (
            <p role="note" className="rounded-lg bg-muted px-4 py-3 text-sm">
              {terms.note}{" "}
              <Link href={routes.terms("de")} className="text-primary underline-offset-2 hover:underline">
                Deutsch
              </Link>
            </p>
          )}
          {terms.sections.map((section) => (
            <section key={section.heading} className="flex flex-col gap-2 text-sm leading-relaxed">
              <h2 className="font-semibold">{section.heading}</h2>
              {section.paragraphs.map((paragraph) => (
                <p key={paragraph} className="text-muted-foreground">
                  {paragraph}
                </p>
              ))}
            </section>
          ))}
        </div>
      </main>
      <SiteFooter locale={locale} t={t} />
    </>
  );
}
