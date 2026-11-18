"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import type { Locale } from "@/lib/locales";

type Text = { join: string; leave: string; joined: string; manage: string; signInToJoin: string };

/**
 * Join, leave and manage on the public community page. The page is one cached
 * render for everyone; who the visitor is, and their role, is asked from the
 * browser afterwards, so nothing personal ever lands in that render.
 */
export function MembershipIsland({
  slug,
  locale,
  active,
  t,
  signInHref,
  manageHref,
}: {
  slug: string;
  locale: Locale;
  active: boolean;
  t: Text;
  signInHref: string;
  manageHref: string;
}) {
  const [state, setState] = useState<{ signedIn: boolean; role: string | null } | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch(`/api/communities/${encodeURIComponent(slug)}/standing`, { cache: "no-store" })
      .then((response) => (response.ok ? response.json() : null))
      .then((json) => !cancelled && setState(json ?? { signedIn: false, role: null }))
      .catch(() => !cancelled && setState({ signedIn: false, role: null }));
    return () => {
      cancelled = true;
    };
  }, [slug]);

  if (!state) {
    return <div className="h-11" aria-hidden />;
  }
  if (!state.signedIn) {
    return active ? (
      <Button asChild className="h-11 self-start">
        <Link href={signInHref}>{t.signInToJoin}</Link>
      </Button>
    ) : null;
  }
  const action = (name: "join" | "leave", label: string, variant: "default" | "outline") => (
    <form method="post" action={`/api/communities/${encodeURIComponent(slug)}/${name}`}>
      <input type="hidden" name="locale" value={locale} />
      <Button type="submit" variant={variant} className="h-11">
        {label}
      </Button>
    </form>
  );
  return (
    <div className="flex flex-wrap items-center gap-2">
      {state.role === null && active && action("join", t.join, "default")}
      {state.role !== null && (
        <>
          <span className="text-sm text-muted-foreground">{t.joined}</span>
          <Button asChild variant="outline" className="h-11">
            <Link href={manageHref}>{t.manage}</Link>
          </Button>
          {state.role !== "OWNER" && action("leave", t.leave, "outline")}
        </>
      )}
    </div>
  );
}
