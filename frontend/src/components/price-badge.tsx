import { Euro } from "lucide-react";

import { INTL_LOCALE, type Locale } from "@/lib/locales";
import { cn } from "@/lib/utils";

/**
 * Free or paid, as the stated price says, on a card, a list row and the event
 * page. Nothing at all when the source named no price: absent is not free.
 */
export function PriceBadge({
  amount,
  currency,
  locale,
  labels,
  className,
}: {
  amount: number | null;
  currency: string | null;
  locale: Locale;
  labels: { free: string; paid: string };
  className?: string;
}) {
  if (amount === null || currency === null) {
    return null;
  }
  const free = amount === 0;
  const price = free
    ? null
    : new Intl.NumberFormat(INTL_LOCALE[locale], { style: "currency", currency, minimumFractionDigits: amount % 1 === 0 ? 0 : 2 }).format(amount);
  return (
    <span
      className={cn(
        "flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-medium",
        free ? "border-primary/30 bg-primary/10 text-primary" : "bg-muted/50 text-muted-foreground",
        className,
      )}
    >
      {!free && <Euro className="h-2.5 w-2.5" />}
      {free ? labels.free : `${labels.paid} · ${price}`}
    </span>
  );
}
