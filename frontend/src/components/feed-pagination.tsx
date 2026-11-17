import Link from "next/link";

import { Button } from "@/components/ui/button";

/**
 * A plain link to another page of the feed. Every page has its own address and
 * is reachable without running a script, or the events past page one do not
 * exist for a crawler (SPEC.md §0.3). With JavaScript the list loads the next
 * pages itself while scrolling (components/feed-list.tsx), so "next" shows only
 * without it; "previous" stays for whoever arrives on a later page.
 */
export function FeedPageLink({
  href,
  rel,
  className,
  children,
}: {
  href: string;
  rel: "prev" | "next";
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <Button asChild variant="outline" size="sm" className={className}>
      <Link href={href} rel={rel}>
        {children}
      </Link>
    </Button>
  );
}
