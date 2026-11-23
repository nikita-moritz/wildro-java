"use client";

import { Pencil } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";

/**
 * "Edit" on the public page of one's own event. The page is one cached render
 * for everyone; whether the visitor wrote this event is asked from the browser
 * afterwards, and nothing shows for anyone else.
 */
export function EditIsland({ slug, href, label }: { slug: string; href: string; label: string }) {
  const [editable, setEditable] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetch(`/api/own/events/${encodeURIComponent(slug)}`, { cache: "no-store" })
      .then((response) => (response.ok ? response.json() : null))
      .then((json: { editable?: boolean } | null) => !cancelled && setEditable(json?.editable === true))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [slug]);

  if (!editable) {
    return null;
  }
  return (
    <Button asChild variant="outline" className="h-11 gap-2 self-start rounded-full px-4">
      <Link href={href}>
        <Pencil className="h-4 w-4" aria-hidden />
        {label}
      </Link>
    </Button>
  );
}
