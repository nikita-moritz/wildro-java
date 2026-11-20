"use client";

import { Check, ChevronDown } from "lucide-react";
import Link from "next/link";

import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { LOCALES, type Locale } from "@/lib/locales";
import { cn } from "@/lib/utils";

const LABELS: Record<Locale, { short: string; name: string }> = {
  de: { short: "DE", name: "Deutsch" },
  en: { short: "EN", name: "English" },
  ru: { short: "RU", name: "Русский" },
};

/**
 * The language menu. Not a native select: Safari draws a select's list around
 * the chosen value, so with RU chosen it opened upwards over the header. This
 * one always opens below the button, and each entry is a real link to the same
 * page in that language.
 */
export function LanguageMenu({
  locale,
  pathname,
  label,
  className,
  align = "end",
  onNavigate,
}: {
  locale: Locale;
  pathname: string;
  label: string;
  className: string;
  /** Which edge of the button the list lines up with: the end in the bar, the start in the mobile panel. */
  align?: "start" | "end";
  onNavigate?: () => void;
}) {
  const rest = pathname.split("/").slice(2).join("/");
  const hrefFor = (each: Locale) => `/${each}${rest ? `/${rest}` : ""}`;

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger aria-label={label} title={label} className={cn(className, "gap-1 px-4 text-sm font-medium")}>
        {LABELS[locale].short}
        <ChevronDown aria-hidden className="h-3.5 w-3.5 opacity-60" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align={align}>
        {LOCALES.map((each) => (
          <DropdownMenuItem key={each} asChild>
            <Link
              href={hrefFor(each)}
              hrefLang={each}
              lang={each}
              aria-current={each === locale ? "page" : undefined}
              onClick={onNavigate}
              className={each === locale ? "font-semibold" : undefined}
            >
              <span>
                <span className="mr-2 inline-block w-6 font-normal text-muted-foreground">{LABELS[each].short}</span>
                {LABELS[each].name}
              </span>
              {each === locale ? <Check aria-hidden className="h-4 w-4 text-primary" /> : null}
            </Link>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
