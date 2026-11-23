import Link from "next/link";

import { LogoMark } from "@/components/logo-mark";
import { currentYear } from "@/lib/current-year";
import type { Dictionary } from "@/lib/dictionaries";
import { footerLinks } from "@/lib/footer-links";
import type { Locale } from "@/lib/locales";

/**
 * The working version's footer, full or compact. The imprint has to be
 * reachable from every page (§ 5 DDG), so app-like views such as the map and the
 * list get the one-line variant rather than none. Terms of use are gone: they
 * governed accounts and posting, which stage one does not have.
 *
 * The link to nikone is a /go/ short link on purpose: it is outbound traffic,
 * and the short link is how nikone.dev sees it came from wildro's footer.
 */
export async function SiteFooter({
  locale,
  t,
  variant = "full",
}: {
  locale: Locale;
  t: Pick<Dictionary, "site" | "footer">;
  variant?: "full" | "compact";
}) {
  const year = await currentYear();

  const nav = (
    <nav className="flex flex-wrap gap-x-6 gap-y-2 text-muted-foreground">
      {footerLinks(locale, t).map(({ href, label }) => (
        <Link key={href} href={href} className="transition-colors hover:text-foreground">
          {label}
        </Link>
      ))}
    </nav>
  );

  const builtBy = (
    <a
      href="https://nikone.dev/go/wildro"
      target="_blank"
      rel="noopener"
      className="underline-offset-2 transition-colors hover:text-foreground hover:underline"
    >
      {t.footer.builtBy}
    </a>
  );

  if (variant === "compact") {
    return (
      <footer className="mt-auto border-t bg-background">
        <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center gap-x-6 gap-y-1 px-4 py-3 text-xs">
          {nav}
          <span className="ml-auto shrink-0 text-muted-foreground">
            © {year} {t.site.name}. {builtBy}
          </span>
        </div>
      </footer>
    );
  }

  return (
    <footer className="mt-auto border-t">
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-4 px-4 py-8 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2 text-sm font-semibold">
          <LogoMark className="h-4 w-4" />
          {t.site.name}
        </div>
        <div className="text-sm">{nav}</div>
        <p className="text-xs text-muted-foreground">
          © {year} {t.site.name}. {t.footer.rights} {builtBy}
        </p>
      </div>
    </footer>
  );
}
