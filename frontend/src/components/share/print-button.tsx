"use client";

import { Printer } from "lucide-react";

import { Button } from "@/components/ui/button";

/** Opens the browser's print dialog, where "Save as PDF" lives too. */
export function PrintButton({ label }: { label: string }) {
  return (
    <Button type="button" className="h-11 gap-2 rounded-full px-4" onClick={() => window.print()}>
      <Printer className="h-4 w-4" aria-hidden />
      {label}
    </Button>
  );
}
