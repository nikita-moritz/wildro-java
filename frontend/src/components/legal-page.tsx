/**
 * The frame of the imprint and the privacy policy. Both are written in German
 * only, the language they are binding in, so under another locale the text is
 * marked as German for screen readers and a line above it says why.
 */
export function LegalPage({
  title,
  subtitle,
  germanOnly,
  back,
  children,
}: {
  title: string;
  subtitle: string;
  germanOnly: string | null;
  /** The way back up the site, drawn above the title. */
  back?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="mx-auto flex max-w-2xl flex-col px-4 py-12">
      {back}
      <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
      {germanOnly && (
        <p role="note" className="mt-4 rounded-lg bg-muted px-4 py-3 text-sm">
          {germanOnly}
        </p>
      )}
      <div lang="de" className="flex flex-col gap-6 text-sm leading-relaxed">
        <p className="mt-2 text-muted-foreground">{subtitle}</p>
        {children}
      </div>
    </div>
  );
}

export function LegalSection({ heading, children }: { heading: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="font-semibold">{heading}</h2>
      <div className="mt-2 flex flex-col gap-2 text-muted-foreground">{children}</div>
    </section>
  );
}

export function LegalLink({ href, children }: { href: string; children: React.ReactNode }) {
  const external = href.startsWith("http");
  return (
    <a
      href={href}
      className="text-primary underline-offset-2 hover:underline"
      {...(external ? { target: "_blank", rel: "noopener noreferrer" } : {})}
    >
      {children}
    </a>
  );
}
