"use client";

import "./elvix.css";

import { ElvixProvider } from "@elvix.is/sdk/react";

import type { Locale } from "@/lib/locales";

/**
 * elvix's provider, mounted by the sign-in and account pages only. Public pages
 * never load the SDK: it would add a bundle and a request to elvix to every
 * page a search visitor opens, for a sign-in almost none of them want.
 */
export function ElvixShell({ clientId, locale, children }: { clientId: string; locale: Locale; children: React.ReactNode }) {
  return (
    <ElvixProvider clientId={clientId} locale={locale}>
      {children}
    </ElvixProvider>
  );
}
