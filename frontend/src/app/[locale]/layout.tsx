import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import "../globals.css";

import { AnalyticsTracker } from "@/components/analytics-tracker";
import { InstallPrompt } from "@/components/install-prompt";
import { SiteHeader, SiteHeaderBar } from "@/components/site-header";
import { ThemeProvider } from "@/components/theme-provider";
import { getDictionary } from "@/lib/dictionaries";
import { brand, siteUrl } from "@/lib/env";
import { FEED_VIEW_SCRIPT } from "@/lib/feed-view";
import { INTL_LOCALE, isLocale, LOCALES } from "@/lib/locales";

/**
 * Inter, as in the working version, self-hosted by next/font so no request
 * leaves for a font CDN. Cyrillic is included: one of the three languages is
 * Russian.
 */
const inter = Inter({ subsets: ["latin", "cyrillic"], variable: "--font-inter", display: "swap" });

export function generateStaticParams() {
  return LOCALES.map((locale) => ({ locale }));
}

export async function generateMetadata({ params }: LayoutProps<"/[locale]">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    return {};
  }
  const t = getDictionary(locale);
  const { name, icons } = brand();
  return {
    metadataBase: new URL(siteUrl()),
    title: { default: `${name}: ${t.site.tagline}`, template: `%s · ${name}` },
    description: t.site.description,
    icons: { icon: "/logo.svg", apple: `${icons}/apple-touch-icon.png` },
    // iOS ignores the manifest's display mode; this opens the home-screen icon full screen.
    appleWebApp: { capable: true, title: name, statusBarStyle: "default" },
    openGraph: { siteName: name, locale: INTL_LOCALE[locale].replace("-", "_"), type: "website" },
  };
}

/** Browser bar and the installed app's title bar in the page colour (app/manifest.ts). */
export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#0a0a0a" },
  ],
};

/**
 * Header here, footer in each page: the working version gives the map and the
 * list a one-line footer and the rest the full one, and a layout cannot tell
 * which page it wraps.
 */
export default async function LocaleLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  // Only strings cross into the client header; the dictionary also holds functions.
  const headerText = { site: { ...t.site, name: brand().name }, nav: t.nav };

  return (
    // next-themes sets the theme class before React hydrates, which React would
    // otherwise report as a mismatch on this one element.
    <html lang={locale} className={`${inter.variable} h-full antialiased`} suppressHydrationWarning>
      <body className="flex min-h-dvh flex-col bg-background">
        {/* List or tiles on the events page, set before anything paints (lib/feed-view.ts). */}
        <script dangerouslySetInnerHTML={{ __html: FEED_VIEW_SCRIPT }} />
        <ThemeProvider>
          <Suspense fallback={<SiteHeaderBar locale={locale} t={headerText} />}>
            <SiteHeader locale={locale} t={headerText} />
          </Suspense>
          {children}
        </ThemeProvider>
        <InstallPrompt t={t.install} icon={`${brand().icons}/icon-192.png`} />
        <AnalyticsTracker />
      </body>
    </html>
  );
}
