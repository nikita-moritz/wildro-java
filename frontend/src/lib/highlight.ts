import { MAX_QUERY_LENGTH } from "@/lib/feed-filter";

/**
 * Which parts of a visible text to mark for a search query (SPEC.md §0.3.1,
 * Подсветка). The folding mirrors the event service's SearchText, so a word
 * is marked exactly where the service found it: case, umlauts and accents do
 * not matter, ß is ss, ё is е. A match by typo or in another language has
 * nothing to mark here, and that is intended.
 */

const MAX_WORDS = 5;

function foldChar(char: string): string {
  const lower = char.toLowerCase().replace("ß", "ss").replace("ё", "е");
  const bare = lower.normalize("NFD").replace(/\p{M}+/gu, "");
  return bare.replace(/[^\p{L}\p{N}]/gu, " ");
}

function fold(text: string): string {
  return Array.from(text, foldChar).join("").replace(/ +/g, " ").trim();
}

/** The query as the service reads it: folded words, single letters dropped, at most five. */
export function searchWords(query: string | null): string[] {
  if (!query) return [];
  const words = fold(query.slice(0, MAX_QUERY_LENGTH))
    .split(" ")
    .filter((word) => word.length > 1);
  return [...new Set(words)].slice(0, MAX_WORDS);
}

export type Segment = { text: string; mark: boolean };

/** The text cut into plain and marked pieces; one plain piece when nothing matches. */
export function highlight(text: string, words: string[]): Segment[] {
  if (words.length === 0 || !text) return [{ text, mark: false }];

  // Folded text with, for each folded character, the index of the original one.
  const chars = Array.from(text);
  let folded = "";
  const origin: number[] = [];
  chars.forEach((char, index) => {
    for (const f of foldChar(char)) {
      folded += f;
      origin.push(index);
    }
  });

  const marked = new Array<boolean>(chars.length).fill(false);
  for (const word of words) {
    for (let at = folded.indexOf(word); at !== -1; at = folded.indexOf(word, at + word.length)) {
      for (let i = at; i < at + word.length; i++) marked[origin[i]] = true;
    }
  }

  const segments: Segment[] = [];
  chars.forEach((char, index) => {
    const last = segments.at(-1);
    if (last && last.mark === marked[index]) last.text += char;
    else segments.push({ text: char, mark: marked[index] });
  });
  return segments;
}
