import Link from "next/link";

import type { Dictionary } from "@/lib/dictionaries";
import type { Locale } from "@/lib/locales";
import { routes } from "@/lib/routes";
import { cn } from "@/lib/utils";

/** Reports and groups: the two queues of the moderation pages. */
export function ModerationTabs({ locale, t, current }: { locale: Locale; t: Dictionary; current: "reports" | "communities" }) {
  const tabs = [
    { key: "reports", href: routes.moderation(locale), label: t.moderation.tabReports },
    { key: "communities", href: routes.moderationCommunities(locale), label: t.moderation.tabCommunities },
  ] as const;
  return (
    <nav aria-label={t.moderation.title} className="flex gap-1 border-b">
      {tabs.map((tab) => (
        <Link
          key={tab.key}
          href={tab.href}
          aria-current={tab.key === current ? "page" : undefined}
          className={cn(
            "-mb-px flex h-11 items-center border-b-2 px-3 text-sm font-medium",
            tab.key === current ? "border-foreground text-foreground" : "border-transparent text-muted-foreground hover:text-foreground",
          )}
        >
          {tab.label}
        </Link>
      ))}
    </nav>
  );
}
