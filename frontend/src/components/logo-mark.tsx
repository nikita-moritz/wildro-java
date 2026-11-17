import { cn } from "@/lib/utils";

/**
 * The logo, painted from public/logo.svg through a CSS mask in the current
 * colour. No outline lives in code: the shape is in that one file.
 */
export function LogoMark({ className }: { className?: string }) {
  return <span aria-hidden="true" className={cn("logo-mask inline-block size-6 bg-primary", className)} />;
}
