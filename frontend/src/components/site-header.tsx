"use client";

import { CalendarCheck, Check, List, Map as MapIcon, Menu, Monitor, Moon, Sun, UserRound, Users, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { useTheme } from "next-themes";

import { LanguageMenu } from "@/components/language-menu";
import { LogoMark } from "@/components/logo-mark";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import type { Dictionary } from "@/lib/dictionaries";
import type { Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { useMounted } from "@/lib/use-mounted";
import { cn } from "@/lib/utils";

/**
 * The working version's navigation: logo, Map, Events and Groups, the language menu,
 * the theme menu and the account link, a hamburger on mobile. Language and theme
 * are menus of the same kind, opening downwards; in the mobile panel the account
 * is a labelled row like the sections, not a bare icon.
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

type HeaderText = Pick<Dictionary, "site" | "nav" | "footer">;

/** One island: the look every item of the bar shares. 44px to the touch on phones, 40px with a mouse. */
const PILL =
  "flex h-11 lg:h-10 items-center rounded-full border bg-card/95 shadow-md backdrop-blur transition-[color,background-color,border-color,box-shadow] duration-150 focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none " +
  // One light for every button of the bar: logo, sections, language, theme,
  // account, menu. On hover, and while a menu it opens is open.
  "hover:border-primary/50 hover:ring-4 hover:ring-primary/20 data-[state=open]:border-primary/50 data-[state=open]:ring-4 data-[state=open]:ring-primary/20";

/**
 * The two groups the pills sit in, left (logo, sections) and right (language,
 * theme, account): filled and shadowed so they stand out from any page and from
 * the map (owner's call, 2026-11-23).
 */
const ISLAND =
  "pointer-events-auto flex items-center gap-1.5 rounded-full border border-foreground/10 bg-island/95 p-1 shadow-lg backdrop-blur";

export function SiteHeader({ locale, t }: { locale: Locale; t: HeaderText }) {
  return <SiteHeaderBar locale={locale} t={t} pathname={usePathname()} />;
}

/** The same bar before the path is known: rendered inside Suspense's fallback. */
export function SiteHeaderBar({ locale, t, pathname = "" }: { locale: Locale; t: HeaderText; pathname?: string }) {
  // The mobile menu is open for the page it was opened on: any navigation closes it.
  const [openOn, setOpenOn] = useState<string | null>(null);
  const open = openOn === pathname;
  const close = () => setOpenOn(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && setOpenOn(null);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  // "/de/account/events" → section "account", sub "events": each item lights for
  // its own part of the site only, never for an address that merely contains its word.
  const [, , section = "", sub = ""] = pathname.split("/");
  const onMap = section === "map";
  const onMyEvents = section === "account" && sub === "events";

  const links = [
    { href: routes.map(locale), label: t.nav.map, icon: MapIcon, active: onMap },
    { href: routes.feed(locale), label: t.nav.events, icon: List, active: section === "events" || section === "event" },
    {
      href: routes.communities(locale),
      label: t.nav.communities,
      icon: Users,
      active: section === "communities" || section === "community",
    },
    // Personal: never prefetched, the page behind it reads the session; a guest signs in first.
    { href: routes.myEvents(locale), label: t.nav.myEvents, icon: CalendarCheck, active: onMyEvents, personal: true },
  ];

  const accountActive = (section === "account" && !onMyEvents) || section === "sign-in" || section === "moderation";
  const accountLink = (
    <Link
      href={routes.account(locale)}
      aria-label={t.nav.account}
      title={t.nav.account}
      aria-current={accountActive ? "page" : undefined}
      // A plain link, never prefetched: the page behind it reads the session.
      prefetch={false}
      onClick={close}
      className={cn(
        PILL,
        "w-11 justify-center lg:w-10",
        accountActive ? "border-primary/30 bg-primary text-primary-foreground" : "text-foreground hover:bg-muted",
      )}
    >
      <UserRound className="h-4 w-4" />
    </Link>
  );

  const languageMenu = (align: "start" | "end") => (
    <LanguageMenu
      align={align}
      locale={locale}
      pathname={pathname}
      label={t.nav.language}
      onNavigate={close}
      className={cn(PILL, "cursor-pointer text-foreground hover:bg-muted data-[state=open]:bg-muted")}
    />
  );

  return (
    // pointer-events-none on the bar and back on each island: the gaps between
    // the pills are not the header's, a click there reaches the map below. Only
    // the map shows through: on every other page the bar has the page's own
    // background, so what scrolls up passes under it instead of between the pills.
    <header className={cn("pointer-events-none sticky top-0 z-50 print:hidden", !onMap && "bg-background")}>
      {/* The bar spans the window on every page (owner's call, 2026-11-23): logo and sections at the left edge, language, theme and account at the right. */}
      <div className="flex h-16 items-center justify-between gap-2 px-3">
        <div className={ISLAND}>
          <Link href={routes.home(locale)} className={cn(PILL, "pointer-events-auto group gap-2 px-4 text-base font-bold hover:bg-muted")}>
            <LogoMark />
            <span className="hidden transition-transform duration-200 group-hover:scale-105 sm:inline-block">
              {t.site.name}
            </span>
          </Link>

          <nav className="hidden items-center gap-1.5 lg:flex">
            {links.map(({ href, label, icon: Icon, active, personal }) => (
              <Link
                key={href}
                href={href}
                prefetch={personal ? false : undefined}
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

        <div className={ISLAND}>
          <div className="hidden items-center gap-1.5 lg:flex">
            {languageMenu("end")}
            <ThemeMenu t={t} align="end" />
            {accountLink}
          </div>

          <button
            type="button"
            aria-label={t.nav.menu}
            aria-expanded={open}
            onClick={() => setOpenOn(open ? null : pathname)}
            className={cn(PILL, "w-11 justify-center text-foreground hover:bg-muted lg:hidden")}
          >
            {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
      </div>

      {open && (
        // Over the page, not in its flow: opening the menu must not push the map or the list down.
        <div className="pointer-events-auto absolute inset-x-3 top-full flex flex-col gap-2 rounded-2xl border bg-card/95 p-3 shadow-xl backdrop-blur lg:hidden">
          {links.map(({ href, label, icon: Icon, active, personal }) => (
            <Link
              key={href}
              href={href}
              prefetch={personal ? false : undefined}
              onClick={close}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex min-h-11 items-center gap-2 rounded-full border px-4 text-sm font-medium transition-colors",
                active ? "border-primary/30 bg-primary text-primary-foreground" : "bg-card hover:bg-muted",
              )}
            >
              <Icon className="h-4 w-4" /> {label}
            </Link>
          ))}
          <Link
            href={routes.account(locale)}
            prefetch={false}
            onClick={close}
            aria-current={accountActive ? "page" : undefined}
            className={cn(
              "flex min-h-11 items-center gap-2 rounded-full border px-4 text-sm font-medium transition-colors",
              accountActive ? "border-primary/30 bg-primary text-primary-foreground" : "bg-card hover:bg-muted",
            )}
          >
            <UserRound className="h-4 w-4" /> {t.nav.account}
          </Link>
          <div className="flex items-center gap-2 border-t pt-3">
            {languageMenu("start")}
            <ThemeMenu t={t} align="start" />
          </div>
          {/* The imprint one tap away on every page (§ 5 DDG): on a phone the footer of a growing list is out of reach. */}
          <nav aria-label={t.footer.legalNotice} className="flex flex-wrap gap-x-5 border-t pt-1 text-sm text-muted-foreground">
            {[
              { href: routes.legalNotice(locale), label: t.footer.legalNotice },
              { href: routes.privacy(locale), label: t.footer.privacy },
              { href: routes.terms(locale), label: t.footer.terms },
            ].map(({ href, label }) => (
              <Link key={href} href={href} onClick={close} className="flex min-h-11 items-center hover:text-foreground">
                {label}
              </Link>
            ))}
          </nav>
        </div>
      )}
    </header>
  );
}

const THEMES = [
  { value: "light", icon: Sun },
  { value: "dark", icon: Moon },
  { value: "system", icon: Monitor },
] as const;

/** Light, dark or as the device: a menu like the language one, the button shows what is set. */
function ThemeMenu({ t, align }: { t: HeaderText; align: "start" | "end" }) {
  const { theme, setTheme } = useTheme();
  const mounted = useMounted();
  const current = THEMES.find((each) => each.value === (mounted ? theme : "system")) ?? THEMES[2];
  const Icon = current.icon;
  const label = `${t.nav.theme.toggle}: ${t.nav.theme[current.value]}`;

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger
        aria-label={label}
        title={label}
        className={cn(PILL, "w-11 cursor-pointer justify-center text-foreground hover:bg-muted data-[state=open]:bg-muted lg:w-10")}
      >
        <Icon className="h-4 w-4" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align={align}>
        {THEMES.map(({ value, icon: ItemIcon }) => (
          <DropdownMenuItem
            key={value}
            onSelect={() => setTheme(value)}
            className={value === current.value ? "font-semibold" : undefined}
          >
            <span className="flex items-center gap-2">
              <ItemIcon aria-hidden className="h-4 w-4 text-muted-foreground" />
              {t.nav.theme[value]}
            </span>
            {value === current.value ? <Check aria-hidden className="h-4 w-4 text-primary" /> : null}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
