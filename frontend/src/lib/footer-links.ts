import type { Dictionary } from "@/lib/dictionaries";
import type { Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";

/**
 * The links every page carries (imprint, privacy, terms, brand), in one list for
 * the footer, the island over the map and the phone menu. The imprint comes
 * first: it has to be reachable from every page (§ 5 DDG).
 */
export function footerLinks(locale: Locale, t: Pick<Dictionary, "footer">): { href: string; label: string }[] {
  return [
    { href: routes.legalNotice(locale), label: t.footer.legalNotice },
    { href: routes.privacy(locale), label: t.footer.privacy },
    { href: routes.terms(locale), label: t.footer.terms },
    { href: routes.brand(locale), label: t.footer.brand },
  ];
}
