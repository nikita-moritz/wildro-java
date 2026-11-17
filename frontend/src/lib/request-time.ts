import "server-only";

import { connection } from "next/server";

/**
 * The current time, read at request time on purpose. connection() makes that
 * explicit to Next, so the value can never be frozen into a prerendered shell at
 * build, and keeping the read out of component bodies keeps render pure.
 */
export async function requestTime(): Promise<number> {
  await connection();
  return Date.now();
}
