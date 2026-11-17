import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { LegalLink, LegalPage, LegalSection } from "@/components/legal-page";
import { SiteFooter } from "@/components/site-footer";
import { getDictionary } from "@/lib/dictionaries";
import { isLocale } from "@/lib/locales";
import { OPERATOR } from "@/lib/operator";
import { routes } from "@/lib/routes";
import { alternatesFor } from "@/lib/seo";

export async function generateMetadata({ params }: PageProps<"/[locale]/legal-notice">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return {
    title: getDictionary(locale).legal.legalNoticeTitle,
    alternates: alternatesFor(routes.legalNotice, locale),
    // Nobody searches for an imprint, and it carries a private address.
    robots: { index: false, follow: true },
  };
}

export default async function LegalNoticePage({ params }: PageProps<"/[locale]/legal-notice">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  return (
    <>
      <main className="flex-1">
        <LegalPage title={t.legal.legalNoticeTitle} subtitle="Angaben gemäß § 5 DDG" germanOnly={t.legal.germanOnly}>
          <LegalSection heading="Diensteanbieter">
            <p className="whitespace-pre-line">
              {[OPERATOR.name, OPERATOR.street, OPERATOR.city, OPERATOR.country].join("\n")}
            </p>
          </LegalSection>

          <LegalSection heading="Kontakt">
            <p>
              E-Mail: <LegalLink href={`mailto:${OPERATOR.email}`}>{OPERATOR.email}</LegalLink>
              <br />
              Telegram: <LegalLink href={OPERATOR.telegram.url}>{OPERATOR.telegram.handle}</LegalLink>
            </p>
          </LegalSection>

          <LegalSection heading="Umsatzsteuer-Identifikationsnummer">
            <p>Umsatzsteuer-Identifikationsnummer gemäß § 27 a Umsatzsteuergesetz: {OPERATOR.vatId}</p>
          </LegalSection>

          <LegalSection heading="Kleinunternehmerregelung">
            <p>Gemäß § 19 UStG wird keine Umsatzsteuer berechnet und nicht gesondert ausgewiesen.</p>
          </LegalSection>

          <LegalSection heading="Verantwortlich für den Inhalt nach § 18 Abs. 2 MStV">
            <p>{OPERATOR.name}, Anschrift wie oben.</p>
          </LegalSection>

          <LegalSection heading="Verbraucherstreitbeilegung">
            <p>
              Wir sind nicht verpflichtet und nicht bereit, an einem Streitbeilegungsverfahren vor einer
              Verbraucherschlichtungsstelle teilzunehmen.
            </p>
          </LegalSection>

          <LegalSection heading="Rolle von wildro">
            <p>
              wildro ist ein Verzeichnis von Outdoor-Events. Die Events stammen aus öffentlich zugänglichen Quellen und
              werden von uns zusammengetragen. wildro ist nicht Veranstalter dieser Events, nimmt keine Anmeldungen
              entgegen und verkauft keine Teilnahme. Veranstalter ist, wer beim jeweiligen Event als Veranstalter genannt
              ist.
            </p>
          </LegalSection>

          <LegalSection heading="Haftung für Inhalte">
            <p>
              Die Inhalte dieser Seiten wurden mit Sorgfalt zusammengestellt. Für Richtigkeit, Vollständigkeit und
              Aktualität können wir keine Gewähr übernehmen: Termine, Treffpunkte und Bedingungen können sich ändern
              oder ein Event kann ausfallen. Maßgeblich sind immer die Angaben beim Veranstalter. Prüfe sie dort, bevor
              du losgehst.
            </p>
          </LegalSection>

          <LegalSection heading="Haftung für Links">
            <p>
              Jedes Event verlinkt auf seine Quelle. Auf die Inhalte dieser fremden Seiten haben wir keinen Einfluss;
              verantwortlich ist deren Betreiber. Zum Zeitpunkt der Verlinkung waren keine Rechtsverstöße erkennbar.
              Erfahren wir von einem Rechtsverstoß, entfernen wir den Link.
            </p>
          </LegalSection>

          <LegalSection heading="Für Veranstalter">
            <p>
              Dein Event steht hier und soll geändert oder entfernt werden? Schreib an{" "}
              <LegalLink href={`mailto:${OPERATOR.email}`}>{OPERATOR.email}</LegalLink>, wir kümmern uns darum.
            </p>
          </LegalSection>
        </LegalPage>
      </main>
      <SiteFooter locale={locale} t={t} />
    </>
  );
}
