import Link from "next/link";

import type { Dictionary } from "@/lib/dictionaries";
import type { Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { cn } from "@/lib/utils";

/**
 * Impressum and Datenschutz, one click away from every page (§ 5 DDG), on pages
 * where the footer is not in reach: the map has none, and the events list keeps
 * pushing its own down while it grows. Where it sits, and from which width it
 * floats, is the page's call; the floating look is the `legal-island` utility
 * in globals.css, written once for both.
 */
export function LegalIsland({
  locale,
  t,
  className,
}: {
  locale: Locale;
  t: Pick<Dictionary, "footer">;
  className?: string;
}) {
  return (
    <nav aria-label={t.footer.legalNotice} className={cn("flex gap-4 text-xs text-muted-foreground", className)}>
      <Link href={routes.legalNotice(locale)} className="hover:text-foreground">
        {t.footer.legalNotice}
      </Link>
      <Link href={routes.privacy(locale)} className="hover:text-foreground">
        {t.footer.privacy}
      </Link>
    </nav>
  );
}
