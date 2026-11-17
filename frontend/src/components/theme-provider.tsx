"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";

/** Light, dark, or the system's choice, as a class on <html>, like the working version. */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  return (
    <NextThemesProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      {children}
    </NextThemesProvider>
  );
}
