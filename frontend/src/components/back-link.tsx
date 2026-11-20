import { ArrowLeft } from "lucide-react";
import Link from "next/link";

import { cn } from "@/lib/utils";

/**
 * The way back to the page above this one, the same on every page that has one:
 * an arrow and a word, 44px to the touch. A link to a fixed address rather than
 * history.back(): someone who arrived from a search result goes up the site, not
 * back to the search engine.
 */
export function BackLink({ href, label, className }: { href: string; label: string; className?: string }) {
  return (
    <Link
      href={href}
      className={cn(
        "-ml-2 flex min-h-11 w-fit items-center gap-1.5 rounded-full px-2 text-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none",
        className,
      )}
    >
      <ArrowLeft aria-hidden className="h-4 w-4" />
      {label}
    </Link>
  );
}
