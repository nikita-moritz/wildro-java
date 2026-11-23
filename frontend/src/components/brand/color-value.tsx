"use client";

import { Check, Copy } from "lucide-react";
import { useState, useSyncExternalStore } from "react";

/**
 * A theme colour's value as the browser has it, in hex: read from the CSS
 * variable on the swatch itself, so the page never states a value of its own
 * that could drift from globals.css. Follows the theme as it changes.
 */
export function ColorValue({ variable, label, copied }: { variable: string; label: string; copied: string }) {
  const [element, setElement] = useState<HTMLSpanElement | null>(null);
  const hex = useSyncExternalStore(
    subscribe,
    () => {
      if (!element) {
        return "";
      }
      const style = getComputedStyle(element);
      return toHex(style.getPropertyValue(variable), style.getPropertyValue("--background"));
    },
    () => "",
  );
  const [done, setDone] = useState(false);

  return (
    <span ref={setElement} className="flex items-center gap-2">
      <code className="font-mono text-sm">{hex || "…"}</code>
      {hex && (
        <button
          type="button"
          aria-label={`${label} ${hex}`}
          onClick={() => {
            void navigator.clipboard?.writeText(hex).catch(() => {});
            setDone(true);
            setTimeout(() => setDone(false), 2000);
          }}
          className="flex h-11 w-11 items-center justify-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground sm:h-8 sm:w-8"
        >
          {done ? <Check className="h-4 w-4" aria-label={copied} /> : <Copy className="h-4 w-4" />}
        </button>
      )}
    </span>
  );
}

/** Theme changes are a class on <html>: watch it. */
function subscribe(onChange: () => void) {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
  return () => observer.disconnect();
}

/**
 * Any CSS colour (oklch, hex) to #rrggbb, by letting a 1-pixel canvas paint it.
 * Painted over the page's ground first, so a see-through colour (the dark
 * theme's line) gives the colour one actually sees.
 */
function toHex(color: string, ground: string): string {
  if (!color.trim()) {
    return "";
  }
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = 1;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) {
    return "";
  }
  context.fillStyle = ground.trim() || "#ffffff";
  context.fillRect(0, 0, 1, 1);
  context.fillStyle = color.trim();
  context.fillRect(0, 0, 1, 1);
  const [r = 0, g = 0, b = 0] = context.getImageData(0, 0, 1, 1).data;
  return `#${[r, g, b].map((value) => value.toString(16).padStart(2, "0")).join("")}`;
}
