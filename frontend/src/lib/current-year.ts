import "server-only";

import { cacheLife } from "next/cache";

/** The year in the footer, cached for a day rather than read on every render. */
export async function currentYear(): Promise<number> {
  "use cache";
  cacheLife("days");
  return new Date().getFullYear();
}
