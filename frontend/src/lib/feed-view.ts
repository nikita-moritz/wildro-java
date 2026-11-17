/**
 * List or tiles on the events page (SPEC.md §0.3.2), kept in the visitor's own
 * browser and named in the privacy policy, section 4.
 *
 * The choice travels as an attribute on <html>, so CSS alone switches the cards
 * (the `tiles:` and `scripted:` variants in globals.css, which spell the same
 * attribute): the list is never re-rendered for it, and the script below sets it
 * before the first paint, so a tiles visitor never sees the list flash.
 */
export type FeedView = "list" | "tiles";

const KEY = "wildro-feed-view";
const ATTRIBUTE = "data-feed-view";

/**
 * Inline at the top of the locale layout's body: runs once per full page load,
 * before anything is painted. Storage may be off; the page then shows the list.
 */
export const FEED_VIEW_SCRIPT = `(function(){var v="list";try{if(localStorage.getItem("${KEY}")==="tiles")v="tiles"}catch(e){}document.documentElement.setAttribute("${ATTRIBUTE}",v)})()`;

const listeners = new Set<() => void>();

export function subscribeFeedView(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function getFeedView(): FeedView {
  return document.documentElement.getAttribute(ATTRIBUTE) === "tiles" ? "tiles" : "list";
}

export function setFeedView(view: FeedView): void {
  document.documentElement.setAttribute(ATTRIBUTE, view);
  try {
    localStorage.setItem(KEY, view);
  } catch {
    // Storage off or full: the choice holds until the next full page load.
  }
  listeners.forEach((listener) => listener());
}
