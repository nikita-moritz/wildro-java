/** The outcome line at the top of a personal page: a success, or a refusal in words. */
export function Notice({ text, tone = "status" }: { text: string | null; tone?: "status" | "alert" }) {
  if (!text) {
    return null;
  }
  return tone === "alert" ? (
    <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
      {text}
    </p>
  ) : (
    <p role="status" className="rounded-lg bg-muted px-4 py-3 text-sm">
      {text}
    </p>
  );
}
