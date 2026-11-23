import { Check, Download, X } from "lucide-react";
import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { ColorValue } from "@/components/brand/color-value";
import { LogoMark } from "@/components/logo-mark";
import { SiteFooter } from "@/components/site-footer";
import { Button } from "@/components/ui/button";
import { getDictionary } from "@/lib/dictionaries";
import { isLocale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { alternatesFor } from "@/lib/seo";
import { cn } from "@/lib/utils";

export async function generateMetadata({ params }: PageProps<"/[locale]/brand">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale).brand;
  return { title: t.title, description: t.lead, alternates: alternatesFor(routes.brand, locale) };
}

/** The theme's colours worth naming, by their CSS variable; the values come from globals.css. */
const COLORS = [
  { variable: "--primary", swatch: "bg-primary" },
  { variable: "--foreground", swatch: "bg-foreground" },
  { variable: "--muted-foreground", swatch: "bg-muted-foreground" },
  { variable: "--border", swatch: "bg-border" },
  { variable: "--background", swatch: "bg-background" },
] as const;

/**
 * The brand page (owner's call, 2026-11-23): logo, name, colours, type and how
 * to use them, for the press, partners and groups pointing to wildro. Nothing
 * on it is drawn twice: the mark is public/logo.svg, the colours are the
 * theme's variables, the type is the site's own.
 */
export default async function BrandPage({ params }: PageProps<"/[locale]/brand">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  const b = t.brand;

  const samples = [
    { label: b.onLight, scope: "theme-light bg-background", mark: "bg-primary", text: "text-foreground" },
    { label: b.onDark, scope: "dark bg-background", mark: "bg-primary", text: "text-foreground" },
    { label: b.onGreen, scope: "theme-light bg-primary", mark: "bg-primary-foreground", text: "text-primary-foreground" },
  ];

  return (
    <>
      <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-14 px-4 py-10">
        <div className="flex flex-col gap-3">
          <h1 className="text-3xl font-semibold tracking-tight">{b.title}</h1>
          <p className="max-w-2xl text-muted-foreground">{b.lead}</p>
        </div>

        <section className="flex flex-col gap-5">
          <Heading title={b.logoTitle} lead={b.logoLead} />
          <div className="grid gap-4 sm:grid-cols-3">
            {samples.map((sample) => (
              <figure key={sample.label} className="flex flex-col gap-2">
                <div className={cn("flex aspect-[4/3] items-center justify-center gap-3 rounded-2xl border", sample.scope)}>
                  <LogoMark className={cn("size-12", sample.mark)} />
                  <span className={cn("text-3xl font-bold tracking-tight", sample.text)}>{t.site.name}</span>
                </div>
                <figcaption className="text-sm text-muted-foreground">{sample.label}</figcaption>
              </figure>
            ))}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" className="h-11 gap-2">
              <a href="/logo.svg" download="wildro-logo.svg">
                <Download className="h-4 w-4" aria-hidden />
                {b.downloadSvg}
              </a>
            </Button>
            <Button asChild variant="outline" className="h-11 gap-2">
              <a href="/icons/icon-512.png" download="wildro-icon-512.png">
                <Download className="h-4 w-4" aria-hidden />
                {b.downloadIcon}
              </a>
            </Button>
          </div>
        </section>

        <section className="flex flex-col gap-3">
          <Heading title={b.nameTitle} lead={b.nameText} />
        </section>

        <section className="flex flex-col gap-5">
          <Heading title={b.colorsTitle} lead={b.colorsLead} />
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {COLORS.map(({ variable, swatch }) => (
              <li key={variable} className="flex items-center gap-4 rounded-2xl border bg-card p-3">
                <span className={cn("size-14 shrink-0 rounded-xl border", swatch)} aria-hidden />
                <span className="flex min-w-0 flex-col">
                  <span className="font-medium">{b.colors[variable]}</span>
                  <ColorValue variable={variable} label={b.copy} copied={b.copied} />
                </span>
              </li>
            ))}
          </ul>
        </section>

        <section className="flex flex-col gap-5">
          <Heading title={b.typeTitle} lead={b.typeLead} />
          <div className="flex flex-col gap-3 rounded-2xl border bg-card p-6">
            <p className="text-4xl font-bold tracking-tight">Inter Bold</p>
            <p className="text-2xl font-semibold">Inter Semibold</p>
            <p className="text-lg">Inter Regular. {b.typeSample}</p>
          </div>
        </section>

        <section className="flex flex-col gap-5">
          <Heading title={b.rulesTitle} />
          <div className="grid gap-4 sm:grid-cols-2">
            <Rules title={b.doTitle} items={b.do} good />
            <Rules title={b.dontTitle} items={b.dont} />
          </div>
        </section>
      </main>
      <SiteFooter locale={locale} t={t} />
    </>
  );
}

function Heading({ title, lead }: { title: string; lead?: string }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
      {lead && <p className="max-w-2xl text-sm text-muted-foreground">{lead}</p>}
    </div>
  );
}

function Rules({ title, items, good = false }: { title: string; items: readonly string[]; good?: boolean }) {
  const Icon = good ? Check : X;
  return (
    <div className="flex flex-col gap-3 rounded-2xl border bg-card p-5">
      <h3 className="font-semibold">{title}</h3>
      <ul className="flex flex-col gap-2 text-sm">
        {items.map((item) => (
          <li key={item} className="flex gap-2">
            <Icon className={cn("mt-0.5 h-4 w-4 shrink-0", good ? "text-primary" : "text-destructive")} aria-hidden />
            {item}
          </li>
        ))}
      </ul>
    </div>
  );
}
