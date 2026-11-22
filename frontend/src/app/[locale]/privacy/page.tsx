import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { BackLink } from "@/components/back-link";
import { LegalLink, LegalPage, LegalSection } from "@/components/legal-page";
import { MapConsentWithdraw } from "@/components/map-consent";
import { SiteFooter } from "@/components/site-footer";
import { getDictionary } from "@/lib/dictionaries";
import { isLocale } from "@/lib/locales";
import { LEGAL_TEXTS_AS_OF, OPERATOR } from "@/lib/operator";
import { routes } from "@/lib/routes";
import { alternatesFor } from "@/lib/seo";

export async function generateMetadata({ params }: PageProps<"/[locale]/privacy">): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  return {
    title: getDictionary(locale).legal.privacyTitle,
    alternates: alternatesFor(routes.privacy, locale),
    robots: { index: false, follow: true },
  };
}

/**
 * Describes what this app does and nothing it does not: an account only for
 * those who sign in, one cookie only for them, no third-party analytics, no
 * access log at the proxy. Every sentence here is a claim
 * about the code or the server, so a change there (an access log in Caddy, a
 * counter, an ad, the /go/{id} redirector) is a change here in the same commit.
 */
export default async function PrivacyPage({ params }: PageProps<"/[locale]/privacy">) {
  const { locale } = await params;
  if (!isLocale(locale)) {
    notFound();
  }
  const t = getDictionary(locale);
  return (
    <>
      <main className="flex-1">
        <LegalPage
          title={t.legal.privacyTitle}
          subtitle={`Stand: ${LEGAL_TEXTS_AS_OF}`}
          germanOnly={t.legal.germanOnly}
          back={<BackLink href={routes.home(locale)} label={t.nav.back} />}
        >
          <LegalSection heading="1. Verantwortlicher">
            <p>
              {OPERATOR.name}, {OPERATOR.street}, {OPERATOR.city}, {OPERATOR.country}
              <br />
              E-Mail: <LegalLink href={`mailto:${OPERATOR.email}`}>{OPERATOR.email}</LegalLink>
            </p>
          </LegalSection>

          <LegalSection heading="2. Kurz gesagt">
            <p>
              wildro kann ohne Konto und ohne Anmeldung gelesen werden. Ein Konto brauchst du nur, wenn du selbst
              etwas veröffentlichen möchtest (Abschnitt 9). Cookies setzen wir nur, wenn du dich anmeldest, und nur
              das eine, das die Anmeldung braucht. Wir nutzen keine Analyse- oder Tracking-Dienste anderer Anbieter
              und zeigen keine Werbung. Wie oft welche Seite aufgerufen
              wird, zählen wir selbst, ohne Cookies und ohne deine IP-Adresse zu speichern (Abschnitt 3). Von fremden
              Servern lädt dein Browser nur zweierlei:
              die Karte, und die erst nach deinem Klick (Abschnitt 5), und die Bilder aus unserem Speicher bei Cloudflare
              (Abschnitt 6).
            </p>
          </LegalSection>

          <LegalSection heading="3. Aufruf der Seite und Hosting">
            <p>
              Die Seite läuft auf einem Server der Hetzner Online GmbH, Industriestr. 25, 91710 Gunzenhausen, in einem
              Rechenzentrum in Deutschland. Hetzner verarbeitet die Daten in unserem Auftrag (Art. 28 DSGVO).
            </p>
            <p>
              Bei jedem Aufruf überträgt dein Browser technisch bedingt deine IP-Adresse, die aufgerufene Adresse, den
              Zeitpunkt und Angaben zu Browser und Betriebssystem. Der Server braucht diese Daten, um die Seite
              auszuliefern. Ein Zugriffsprotokoll mit IP-Adressen führen wir nicht. Rechtsgrundlage ist unser
              berechtigtes Interesse am Betrieb der Seite (Art. 6 Abs. 1 lit. f DSGVO).
            </p>
            <p>
              Reichweitenmessung: Damit wir wissen, welche Seiten gelesen werden, zählen wir die Aufrufe selbst auf
              unserem Server, ohne Dienste anderer Anbieter und ohne Cookies. Gespeichert werden die aufgerufene Seite,
              ihre Sprache, die Adresse der Seite, von der du gekommen bist, Kampagnenkennzeichen in der Adresse
              (utm-Parameter) und der Zeitpunkt. Um mehrere Aufrufe desselben Tages zusammenzufassen, bilden wir aus
              deiner IP-Adresse, den Angaben zu deinem Browser, dem Tagesdatum und einem nur uns bekannten Geheimwert
              einen Prüfwert. IP-Adresse und Browserangaben speichern wir dabei nicht. Weil das Tagesdatum eingeht,
              ändert sich der Prüfwert jeden Tag, sodass wir dich an einem anderen Tag nicht wiedererkennen. Wir
              führen ihn mit keinen anderen Daten zusammen. Auf deinem Gerät wird nichts gespeichert. Aufrufe von
              Suchmaschinen und anderen automatischen Programmen zählen wir nicht. Die Einträge löschen wir nach 14
              Monaten. Rechtsgrundlage ist unser berechtigtes Interesse an einer bedarfsgerechten Gestaltung der Seite
              (Art. 6 Abs. 1 lit. f DSGVO). Du kannst dem jederzeit widersprechen (Abschnitt 11).
            </p>
          </LegalSection>

          <LegalSection heading="4. Cookies und lokaler Speicher">
            <p>
              Solange du nicht angemeldet bist, setzt wildro keine Cookies. Meldest du dich an, setzen wir das Cookie{" "}
              <code className="text-foreground">wildro_session</code>. Es hält deine Anmeldung fest, ist für Skripte
              auf der Seite nicht lesbar, wird nur an wildro gesendet und läuft nach 30 Tagen oder mit dem Abmelden
              ab. Während der Anmeldung legt der Anmeldedienst elvix in deinem Browser außerdem den Eintrag{" "}
              <code className="text-foreground">elvix.session.token</code> im lokalen Speicher ab. Beides ist für die
              von dir gewünschte Anmeldung erforderlich (§ 25 Abs. 2 Nr. 2 TDDDG).
            </p>
            <p>
              Wenn du zwischen hellem und dunklem Design umschaltest, merkt sich dein
              Browser diese Wahl im lokalen Speicher (Eintrag <code className="text-foreground">theme</code>), ebenso
              ob du die Events als Liste oder als Kacheln sehen möchtest (Eintrag{" "}
              <code className="text-foreground">wildro-feed-view</code>). Die Einträge bleiben auf deinem Gerät,
              werden nicht an uns übertragen und sind für die gewünschte Darstellung erforderlich (§ 25 Abs. 2 Nr. 2
              TDDDG). Du kannst sie jederzeit in deinem Browser löschen. Ein weiterer Eintrag entsteht nur, wenn du
              die Karte lädst (Abschnitt 5).
            </p>
          </LegalSection>

          <LegalSection heading="5. Karte">
            <p>
              Die Karte stellt Mapbox bereit, ein Dienst der Mapbox, Inc. mit Sitz in den USA. Sie wird erst geladen,
              wenn du auf der Kartenseite auf „Karte laden“ klickst. Vorher findet keine Verbindung zu Mapbox statt.
            </p>
            <p>
              Nach dem Klick lädt dein Browser Kartenmaterial, Schriften und Programmcode direkt von Mapbox. Dabei
              erhält Mapbox deine IP-Adresse, Angaben zu Browser, Gerät und Betriebssystem, die Adresse der
              aufrufenden Seite, den angezeigten Kartenausschnitt sowie Nutzungsdaten mit einer zufällig erzeugten
              Sitzungskennung. Nach eigenen Angaben löscht Mapbox die IP-Adresse nach 30 Tagen. Mapbox ist nach dem
              EU-US Data Privacy Framework zertifiziert; für die Übermittlung in die USA besteht damit ein
              Angemessenheitsbeschluss der Europäischen Kommission. Näheres steht in der{" "}
              <LegalLink href="https://www.mapbox.com/legal/privacy">Datenschutzerklärung von Mapbox</LegalLink>.
            </p>
            <p>
              Rechtsgrundlage ist deine Einwilligung (Art. 6 Abs. 1 lit. a DSGVO, § 25 Abs. 1 TDDDG). Deine Wahl
              merkt sich dein Browser im lokalen Speicher (Eintrag{" "}
              <code className="text-foreground">wildro-map-consent</code>), damit du nicht jedes Mal gefragt wirst.
              Du kannst die Einwilligung jederzeit mit Wirkung für die Zukunft zurücknehmen:
            </p>
            <MapConsentWithdraw
              label="Einwilligung für die Karte zurücknehmen"
              done="Zurückgenommen. Die Karte wird erst nach einem neuen Klick wieder geladen."
            />
          </LegalSection>

          <LegalSection heading="6. Bilder">
            <p>
              Die Bilder auf wildro liegen unter der Adresse media.wildro.app in einem Speicher der Cloudflare, Inc.,
              101 Townsend Street, San Francisco, CA 94107, USA, mit Speicherort in der Europäischen Union, und werden
              über deren Netz ausgeliefert. Das sind Fotos der Orte aus freien Archiven wie Wikimedia Commons, mit
              Angabe von Urheber und Lizenz, oder Symbolbilder aus unserem eigenen Bestand. Bilder von den Seiten der
              Veranstalter laden wir nicht.
            </p>
            <p>
              Beim Laden eines Bildes verarbeitet Cloudflare in unserem Auftrag (Art. 28 DSGVO) deine IP-Adresse und
              Angaben zu deinem Browser. Cloudflare ist nach dem EU-US Data Privacy Framework zertifiziert. Die Links in
              den Bildnachweisen führen zu Wikimedia Commons und zu den Lizenztexten; eine Verbindung dorthin entsteht
              erst mit deinem Klick.
            </p>
            <p>
              Rechtsgrundlage ist unser berechtigtes Interesse, Events anschaulich zu zeigen (Art. 6 Abs. 1 lit. f
              DSGVO).
            </p>
          </LegalSection>

          <LegalSection heading="7. Standort („In meiner Nähe“)">
            <p>
              Deinen Standort verwenden wir nur, wenn du im Filter auf „In meiner Nähe“ klickst und die Abfrage in
              deinem Browser erlaubst. Noch im Browser wird die Position auf zwei Nachkommastellen gerundet, das ist
              etwa ein Kilometer. Nur dieser gerundete Wert wird als Teil der Seitenadresse an unseren Server
              geschickt, damit er die Events in deiner Umgebung heraussuchen kann. Gespeichert wird er bei uns nicht.
            </p>
            <p>
              Rechtsgrundlage ist deine Einwilligung (Art. 6 Abs. 1 lit. a DSGVO). Du kannst sie jederzeit
              zurücknehmen, indem du den Filter zurücksetzt oder die Standortfreigabe in deinem Browser entziehst.
            </p>
          </LegalSection>

          <LegalSection heading="8. Events und Veranstalter">
            <p>
              Die Events stammen aus öffentlich zugänglichen Quellen. Wir zeigen sie so, wie sie dort veröffentlicht
              sind: Titel, Beschreibung, Termin, Treffpunkt, den Namen des Veranstalters und einen Link zur Quelle. Der
              Name des Veranstalters kann der Name einer Person sein. Rechtsgrundlage ist unser berechtigtes Interesse,
              öffentlich angekündigte Events auffindbar zu machen (Art. 6 Abs. 1 lit. f DSGVO). Die Angaben bleiben
              sichtbar, solange das Event veröffentlicht ist.
            </p>
            <p>
              Bist du Veranstalter und möchtest einen Eintrag ändern oder entfernen lassen, schreib an{" "}
              <LegalLink href={`mailto:${OPERATOR.email}`}>{OPERATOR.email}</LegalLink>. Einem Widerspruch kommen wir
              nach.
            </p>
            <p>
              Der Link zur Quelle, die Links zu sozialen Netzwerken und Messengern (etwa Telegram, Instagram, WhatsApp
              oder Facebook) und der Link „In Google Maps öffnen“ beim Treffpunkt führen auf fremde Seiten. Es sind
              einfache Links, keine eingebetteten Inhalte: Erst mit deinem Klick entsteht eine Verbindung dorthin. Für
              die Datenverarbeitung dort ist der jeweilige Betreiber verantwortlich, bei Google Maps die Google Ireland
              Limited.
            </p>
          </LegalSection>

          <LegalSection heading="9. Konto und Anmeldung">
            <p>
              Die Anmeldung übernimmt elvix, ein Dienst von Edvard Grei (edvone), Jülicher Straße 72a, 52070 Aachen,
              der die Daten in unserem Auftrag verarbeitet (Art. 28 DSGVO). elvix betreibt seine Server bei der
              Hetzner Online GmbH in Helsinki, Finnland. Du meldest dich mit Google, einem Passkey oder einem Code per
              E-Mail an; beim ersten Anmelden entsteht dein Konto. Auf der Anmelde- und der Kontoseite verbindet
              sich dein Browser dafür direkt mit elvix.is. Wählst du Google, erhält Google Ireland Limited die
              Angaben, die für die Anmeldung mit deinem Google-Konto nötig sind. Näheres steht in der{" "}
              <LegalLink href="https://elvix.is/legal/privacy">Datenschutzerklärung von elvix</LegalLink>.
            </p>
            <p>
              Bei uns gespeichert werden genau drei Angaben: die Kennung deines Kontos bei elvix, deine
              E-Mail-Adresse und dein Anzeigename, den du jederzeit ändern kannst. Dazu kommt, welche Fassung der
              Nutzungsregeln du wann angenommen hast. Ein Profilbild übernehmen wir nicht. Deine E-Mail-Adresse
              sieht niemand außer dir; wir schreiben dir nur, wenn es um deine Beiträge geht. Rechtsgrundlage ist
              die Erfüllung des Nutzungsverhältnisses (Art. 6 Abs. 1 lit. b DSGVO).
            </p>
            <p>
              Veröffentlichst du ein eigenes Event, ist es mit deinem Anzeigenamen für alle sichtbar, bis du es
              absagst oder es vergangen ist; vergangene Events bleiben als Archiv sichtbar. Entwürfe siehst nur du.
              Was du dort einträgst, entscheidest du selbst; Angaben über andere Menschen gehören nicht hinein
              (siehe Nutzungsregeln). Rechtsgrundlage ist die Erfüllung des Nutzungsverhältnisses (Art. 6 Abs. 1
              lit. b DSGVO).
            </p>
            <p>
              Trittst du einer Gruppe bei, speichern wir die Mitgliedschaft, deine Rolle darin und deinen
              Anzeigenamen. Die Namen der Mitglieder sehen nur Mitglieder derselben Gruppe; öffentlich ist nur ihre
              Zahl. Verlässt du die Gruppe oder löschst du dein Konto, wird die Mitgliedschaft gelöscht. Gehört dir
              eine Gruppe, geht sie beim Löschen des Kontos an den Admin über, der es am längsten ist, sonst wird
              sie geschlossen. Rechtsgrundlage ist die Erfüllung des Nutzungsverhältnisses (Art. 6 Abs. 1 lit. b
              DSGVO).
            </p>
            <p>
              Inhaber und Admins einer Gruppe können ein Bild für sie hochladen, etwa ein Logo. Beim Hochladen
              schneiden wir es auf 512 mal 512 Pixel zu, speichern es als WebP und entfernen dabei alle Metadaten,
              auch Standort und Kamera. Das Bild liegt auf unserem Server bei der Hetzner Online GmbH in Deutschland
              und ist öffentlich mit der Gruppe zu sehen, bis es ersetzt oder entfernt wird; dann löschen wir die
              Datei. Erkennbare Personen gehören nur mit ihrer Einwilligung hinein (siehe Nutzungsregeln).
              Rechtsgrundlage ist die Erfüllung des Nutzungsverhältnisses (Art. 6 Abs. 1 lit. b DSGVO).
            </p>
            <p>
              Meldest du ein Event oder eine Gruppe, speichern wir den gewählten Grund, deinen Text, den Zeitpunkt
              und, bei einem Event, falls du angemeldet bist, die Kennung deines Kontos, um die Meldung zu prüfen (Art. 6 Abs. 1 lit. c DSGVO in
              Verbindung mit Art. 16 DSA). Deine IP-Adresse speichern wir dafür nicht: Sie wird höchstens eine Stunde
              lang im Arbeitsspeicher gehalten, um mehr als fünf Meldungen pro Stunde von derselben Adresse
              abzuweisen.
            </p>
            <p>
              Dein Konto kannst du auf der Kontoseite selbst löschen. Dann löschen wir Anzeigenamen und
              E-Mail-Adresse, und deine künftigen eigenen Events werden abgesagt. Eine Kopie deiner Daten schicken
              wir dir auf Anfrage an die oben genannte Adresse.
            </p>
          </LegalSection>

          <LegalSection heading="10. Kontakt per E-Mail">
            <p>
              Schreibst du uns eine E-Mail, verarbeiten wir deine Adresse und den Inhalt deiner Nachricht, um sie zu
              beantworten (Art. 6 Abs. 1 lit. f DSGVO). Unser Postfach wird von Google Workspace bereitgestellt, einem
              Dienst der Google Ireland Limited, Gordon House, Barrow Street, Dublin 4, Irland, der die Daten in
              unserem Auftrag verarbeitet. Wir löschen die Nachricht, sobald dein Anliegen erledigt ist und keine
              gesetzliche Aufbewahrungspflicht entgegensteht.
            </p>
          </LegalSection>

          <LegalSection heading="11. Deine Rechte">
            <p>
              Du hast das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der Verarbeitung,
              Datenübertragbarkeit und Widerspruch (Art. 15 bis 21 DSGVO). Eine Einwilligung kannst du jederzeit mit
              Wirkung für die Zukunft widerrufen. Wende dich dafür an die oben genannte Adresse.
            </p>
            <p>
              Außerdem kannst du dich bei einer Aufsichtsbehörde beschweren, in Nordrhein-Westfalen bei der
              Landesbeauftragten für Datenschutz und Informationsfreiheit Nordrhein-Westfalen.
            </p>
          </LegalSection>
        </LegalPage>
      </main>
      <SiteFooter locale={locale} t={t} />
    </>
  );
}
