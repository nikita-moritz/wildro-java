import { de } from "@/lib/dictionaries/de";
import { en } from "@/lib/dictionaries/en";
import { ru } from "@/lib/dictionaries/ru";
import type { Dictionary } from "@/lib/dictionaries/types";
import type { Locale } from "@/lib/locales";

const DICTIONARIES: Record<Locale, Dictionary> = { de, en, ru };

export function getDictionary(locale: Locale): Dictionary {
  return DICTIONARIES[locale];
}

export type { Dictionary };
