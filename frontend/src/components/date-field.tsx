"use client";

import { CalendarDays, X } from "lucide-react";
import { useState, useSyncExternalStore } from "react";
import { de } from "react-day-picker/locale/de";
import { enGB } from "react-day-picker/locale/en-GB";
import { ru } from "react-day-picker/locale/ru";

import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { INTL_LOCALE, type Locale } from "@/lib/locales";
import { cn } from "@/lib/utils";

/**
 * A date in a form, in the page's language. The browser's own date input
 * follows the language of the browser, not of the page, so a Russian browser
 * showed "дд.мм.гггг" inside a German filter panel.
 *
 * Without JavaScript the browser's input is all there is, and it still works:
 * the form stays a plain GET form. Once the page is live it is swapped for a
 * button and a calendar that write the same YYYY-MM-DD value under the same
 * name, so nothing downstream can tell the two apart.
 */
export const CALENDAR_LOCALE = { de, en: enGB, ru };

const LABEL = "flex flex-col gap-1 text-xs text-muted-foreground";

const subscribe = () => () => {};

export function parseIso(value: string): Date | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  return match ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : undefined;
}

export function toIso(date: Date): string {
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function DateField({
  name,
  label,
  defaultValue,
  locale,
  placeholder,
  clearLabel,
  className,
}: {
  name: string;
  label: string;
  defaultValue: string;
  locale: Locale;
  /** The date pattern as the page's language writes it, shown while nothing is chosen. */
  placeholder: string;
  clearLabel: string;
  className?: string;
}) {
  const live = useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );
  const [value, setValue] = useState(defaultValue);
  const [open, setOpen] = useState(false);

  if (!live) {
    return (
      <label className={LABEL}>
        {label}
        <input type="date" name={name} defaultValue={defaultValue} className={className} />
      </label>
    );
  }

  const selected = parseIso(value);
  const shown = selected
    ? new Intl.DateTimeFormat(INTL_LOCALE[locale], { day: "2-digit", month: "2-digit", year: "numeric" }).format(selected)
    : placeholder;

  return (
    // Not a <label>: it would wrap two buttons and lend its text to both.
    <div className={LABEL}>
      <span aria-hidden="true">{label}</span>
      <div className="relative">
        <input type="hidden" name={name} value={value} />
        <Popover open={open} onOpenChange={setOpen}>
          <PopoverTrigger
            type="button"
            aria-label={`${label}: ${shown}`}
            className={cn(
              className,
              "flex items-center justify-between gap-1 text-left",
              selected ? "text-foreground" : "text-muted-foreground",
            )}
          >
            <span className="truncate">{shown}</span>
            {!selected && <CalendarDays className="h-4 w-4 shrink-0" />}
          </PopoverTrigger>
          {/* Above the filter form, which itself sits above the map at z-600. */}
          <PopoverContent align="start" className="z-700 w-auto p-0">
            <Calendar
              mode="single"
              locale={CALENDAR_LOCALE[locale]}
              selected={selected}
              defaultMonth={selected}
              onSelect={(day) => {
                setValue(day ? toIso(day) : "");
                setOpen(false);
              }}
            />
          </PopoverContent>
        </Popover>
        {selected && (
          <button
            type="button"
            aria-label={`${label}: ${clearLabel}`}
            onClick={() => setValue("")}
            className="absolute top-1/2 right-1 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded text-muted-foreground hover:text-foreground"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>
    </div>
  );
}
