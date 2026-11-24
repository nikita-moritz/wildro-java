import { notFound } from "next/navigation";

/**
 * Any address under a language that no page answers, such as /de/foo: the
 * language's own not-found page, with header and footer, instead of Next's bare
 * English one, which a catch-all under the root layout would otherwise give.
 */
export default function UnknownPage(): never {
  notFound();
}
