import { useSyncExternalStore } from "react";

const subscribe = () => () => {};

/**
 * False on the server and while hydrating, true after: for what may only differ
 * from the server's HTML once the page is interactive. No setState in an effect.
 */
export function useMounted(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );
}
