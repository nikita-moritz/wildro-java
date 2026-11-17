import { highlight } from "@/lib/highlight";

/** Text with the searched words marked (SPEC.md §0.3.1, Подсветка). */
export function Highlighted({ text, words }: { text: string; words: string[] }) {
  return (
    <>
      {highlight(text, words).map((segment, index) =>
        segment.mark ? (
          <mark key={index} className="rounded-sm bg-primary/15 px-0.5 text-inherit">
            {segment.text}
          </mark>
        ) : (
          segment.text
        ),
      )}
    </>
  );
}
