import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * A step that cannot be taken back, asked twice. The first button only opens
 * the question; the second one sends the form. A <details> element, so it works
 * without JavaScript like every other form on these pages.
 */
export function ConfirmForm({
  action,
  fields,
  label,
  question,
  yes,
  size = "touch",
}: {
  action: string;
  fields: Record<string, string>;
  label: string;
  question: string;
  yes: string;
  size?: "sm" | "touch";
}) {
  return (
    <details className="group">
      <summary
        className={cn(
          buttonVariants({ variant: "outline", size }),
          "cursor-pointer list-none text-destructive group-open:bg-muted [&::-webkit-details-marker]:hidden",
        )}
      >
        {label}
      </summary>
      <form method="post" action={action} className="mt-2 flex max-w-sm flex-col gap-3 rounded-xl border bg-card p-3 text-sm">
        {Object.entries(fields).map(([name, value]) => (
          <input key={name} type="hidden" name={name} value={value} />
        ))}
        <p>{question}</p>
        <Button type="submit" variant="destructive" size="touch" className="self-start">
          {yes}
        </Button>
      </form>
    </details>
  );
}
