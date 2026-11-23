"use client";

import { CalendarDays, X } from "lucide-react";
import { useState } from "react";

import { CALENDAR_LOCALE, parseIso, toIso } from "@/components/date-field";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { INTL_LOCALE, type Locale } from "@/lib/locales";
import { cn } from "@/lib/utils";

/** Quarter hours: what an event's start is in practice, and a list short enough to scroll. */
const TIMES = Array.from({ length: 96 }, (_, i) => `${String(Math.floor(i / 4)).padStart(2, "0")}:${String((i % 4) * 15).padStart(2, "0")}`);

/**
 * A date and a time in a form, in the page's language: the browser's
 * datetime-local field speaks the browser's language instead, so a Russian
 * browser showed "дд.мм.гггг, --:--" in a German editor. A calendar for the
 * day (as in the filters, components/date-field.tsx) and a list of times.
 *
 * The form gets the same "YYYY-MM-DDTHH:MM" under the same name as before, so
 * nothing downstream changes. The value lives in a visually hidden input that
 * carries `required`, so the browser still refuses to send a form without it.
 */
export function DateTimeField({
  name,
  label,
  timeLabel,
  defaultValue,
  defaultTime,
  required = false,
  locale,
  placeholder,
  clearLabel,
  className,
}: {
  name: string;
  label: string;
  timeLabel: string;
  /** "YYYY-MM-DDTHH:MM" or empty. */
  defaultValue: string;
  /** "HH:MM" to start from when there is no day yet: a copied event keeps its time, not its date. */
  defaultTime?: string;
  required?: boolean;
  locale: Locale;
  placeholder: string;
  clearLabel: string;
  className: string;
}) {
  const [day, setDay] = useState(defaultValue.slice(0, 10));
  const [time, setTime] = useState(defaultValue.slice(11, 16) || defaultTime || "10:00");
  const [open, setOpen] = useState(false);
  // Read when the calendar opens, never while rendering: a render must not depend on the clock.
  const [today, setToday] = useState<Date | null>(null);

  const selected = parseIso(day);
  const value = selected ? `${day}T${time}` : "";
  const shown = selected
    ? new Intl.DateTimeFormat(INTL_LOCALE[locale], { weekday: "short", day: "2-digit", month: "2-digit", year: "numeric" }).format(selected)
    : placeholder;
  // A time saved off the quarter hour (an older event, say) stays choosable.
  const times = TIMES.includes(time) ? TIMES : [...TIMES, time].sort();

  return (
    // Not a <label>: it would wrap two controls and lend its text to both.
    <div className="flex flex-col gap-1.5 text-sm font-medium">
      <span aria-hidden="true">{label}</span>
      <div className="relative flex gap-2">
        <input
          name={name}
          value={value}
          required={required}
          onChange={() => {}}
          tabIndex={-1}
          aria-hidden="true"
          // Under the day button, so the browser's "fill this in" bubble points at it.
          className="pointer-events-none absolute bottom-0 left-4 h-px w-px opacity-0"
        />
        <div className="relative min-w-0 flex-1">
          <Popover
            open={open}
            onOpenChange={(next) => {
              setOpen(next);
              if (next) {
                setToday(new Date());
              }
            }}
          >
            <PopoverTrigger
              type="button"
              aria-label={`${label}: ${shown}`}
              className={cn(
                className,
                "flex w-full items-center justify-between gap-2 text-left",
                selected ? "text-foreground" : "font-normal text-muted-foreground",
                // Room for the clear button, which sits over the right end.
                selected && !required && "pr-10",
              )}
            >
              <span className="truncate">{shown}</span>
              {!selected && <CalendarDays className="h-4 w-4 shrink-0" />}
            </PopoverTrigger>
            <PopoverContent align="start" className="z-700 w-auto p-0">
              <Calendar
                mode="single"
                locale={CALENDAR_LOCALE[locale]}
                selected={selected}
                defaultMonth={selected}
                // The service refuses an event in the past; the calendar does not offer one.
                disabled={today ? { before: today } : undefined}
                onSelect={(picked) => {
                  setDay(picked ? toIso(picked) : "");
                  setOpen(false);
                }}
              />
            </PopoverContent>
          </Popover>
          {selected && !required && (
            <button
              type="button"
              aria-label={`${label}: ${clearLabel}`}
              onClick={() => setDay("")}
              className="absolute top-1/2 right-1 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded text-muted-foreground hover:text-foreground"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
        <select
          aria-label={`${label}: ${timeLabel}`}
          value={time}
          onChange={(event) => setTime(event.target.value)}
          disabled={!selected}
          className={cn(className, "w-24 shrink-0 disabled:opacity-50")}
        >
          {times.map((each) => (
            <option key={each} value={each}>
              {each}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
