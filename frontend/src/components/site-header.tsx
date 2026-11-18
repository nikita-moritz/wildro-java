"use client";

import { List, Map as MapIcon, Menu, Monitor, Moon, Sun, UserRound, X } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { useTheme } from "next-themes";

import { LogoMark } from "@/components/logo-mark";
import type { Dictionary } from "@/lib/dictionaries";
import { LOCALES, type Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { useMounted } from "@/lib/use-mounted";
import { cn } from "@/lib/utils";

/**
 * The working version's navigation: logo, Map and Events, the language select,
 * the theme toggle and the account link, a hamburger on mobile.
 *
 * The account link is the same for everyone and does not know whether anyone
 * is signed in: knowing would mean reading the session on every page, and
 * every public page would stop being a cached render. The account page itself
 * sends a signed-out visitor on to the sign-in page.
 *
 * Drawn as islands (owner's call, 2026-10-20): every item is its own pill on a
 * transparent bar, so on the map page the bar floats over the map with nothing
 * but the pills covering it. The bar is HEADER_HEIGHT tall; the map page pulls
 * itself up under it by that much.
 */

type HeaderText = Pick<Dictionary, "site" | "nav">;

const LABELS: Record<Locale, string> = { de: "DE", en: "EN", ru: "RU" };

/** One island: the look every item of the bar shares. */
const PILL =
  "flex h-10 items-center rounded-full border bg-card/95 shadow-md backdrop-blur transition-colors focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none";

export function SiteHeader({ locale, t }: { locale: Locale; t: HeaderText }) {
  return <SiteHeaderBar locale={locale} t={t} pathname={usePathname()} />;
}

/** The same bar before the path is known: rendered inside Suspense's fallback. */
export function SiteHeaderBar({ locale, t, pathname = "" }: { locale: Locale; t: HeaderText; pathname?: string }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);

  const links = [
    { href: routes.map(locale), label: t.nav.map, icon: MapIcon, active: pathname.endsWith("/map") },
    {
      href: routes.feed(locale),
      label: t.nav.events,
      icon: List,
      active: pathname.includes("/events") || pathname.includes("/event/"),
    },
  ];

  function switchLanguage(next: string) {
    const rest = pathname.split("/").slice(2).join("/");
    router.push(`/${next}${rest ? `/${rest}` : ""}`);
  }

  const accountActive = pathname.endsWith("/account") || pathname.endsWith("/sign-in");
  const accountLink = (
    <Link
      href={routes.account(locale)}
      aria-label={t.nav.account}
      title={t.nav.account}
      aria-current={accountActive ? "page" : undefined}
      // A plain link, never prefetched: the page behind it reads the session.
      prefetch={false}
      className={cn(
        PILL,
        "w-10 justify-center",
        accountActive ? "border-primary/30 bg-primary text-primary-foreground" : "text-foreground hover:bg-muted",
      )}
    >
      <UserRound className="h-4 w-4" />
    </Link>
  );

  const languageSelect = (
    <select
      aria-label={t.nav.language}
      value={locale}
      onChange={(event) => switchLanguage(event.target.value)}
      className={cn(PILL, "cursor-pointer appearance-none px-4 text-sm font-medium text-foreground hover:bg-muted")}
    >
      {LOCALES.map((each) => (
        <option key={each} value={each}>
          {LABELS[each]}
        </option>
      ))}
    </select>
  );

  return (
    // pointer-events-none on the bar and back on each island: the gaps between
    // the pills are not the header's, a click there reaches the map below.
    <header className="pointer-events-none sticky top-0 z-50">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-2 px-3">
        <div className="flex items-center gap-2">
          <Link href={routes.home(locale)} className={cn(PILL, "pointer-events-auto group gap-2 px-4 text-base font-bold")}>
            <LogoMark />
            <span className="hidden transition-transform duration-200 group-hover:scale-105 sm:inline-block">
              {t.site.name}
            </span>
          </Link>

          <nav className="hidden items-center gap-2 md:flex">
            {links.map(({ href, label, icon: Icon, active }) => (
              <Link
                key={href}
                href={href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  PILL,
                  "pointer-events-auto gap-1.5 px-4 text-sm font-medium",
                  active
                    ? "border-primary/30 bg-primary text-primary-foreground hover:bg-primary/90"
                    : "text-foreground hover:bg-muted",
                )}
              >
                <Icon className="h-4 w-4" /> {label}
              </Link>
            ))}
          </nav>
        </div>

        <div className="pointer-events-auto hidden items-center gap-2 md:flex">
          {languageSelect}
          <ThemeToggle t={t} />
          {accountLink}
        </div>

        <button
          type="button"
          aria-label={t.nav.menu}
          aria-expanded={open}
          onClick={() => setOpen(!open)}
          className={cn(PILL, "pointer-events-auto w-10 justify-center text-foreground hover:bg-muted md:hidden")}
        >
          {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </div>

      {open && (
        // Over the page, not in its flow: opening the menu must not push the map or the list down.
        <div className="pointer-events-auto absolute inset-x-3 top-full flex flex-col gap-2 rounded-2xl border bg-card/95 p-3 shadow-xl backdrop-blur md:hidden">
          {links.map(({ href, label, icon: Icon, active }) => (
            <Link
              key={href}
              href={href}
              onClick={() => setOpen(false)}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex min-h-11 items-center gap-2 rounded-full border px-4 text-sm font-medium transition-colors",
                active ? "border-primary/30 bg-primary text-primary-foreground" : "bg-card hover:bg-muted",
              )}
            >
              <Icon className="h-4 w-4" /> {label}
            </Link>
          ))}
          <div className="flex items-center gap-2 pt-1">
            {languageSelect}
            <ThemeToggle t={t} />
            {accountLink}
          </div>
        </div>
      )}
    </header>
  );
}

/** Cycles dark, light, system, as the working version does, and shows where it stands. */
function ThemeToggle({ t }: { t: HeaderText }) {
  const { theme, setTheme } = useTheme();
  const mounted = useMounted();
  const current = mounted ? theme : "system";
  const next = current === "dark" ? "light" : current === "light" ? "system" : "dark";
  const Icon = current === "dark" ? Sun : current === "light" ? Moon : Monitor;
  const label = `${t.nav.theme.toggle}: ${t.nav.theme[next as "light" | "dark" | "system"]}`;

  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={() => setTheme(next)}
      className={cn(PILL, "w-10 justify-center text-foreground hover:bg-muted")}
    >
      <Icon className="h-4 w-4" />
    </button>
  );
}
