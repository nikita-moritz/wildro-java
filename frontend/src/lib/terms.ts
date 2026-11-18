import type { Locale } from "@/lib/locales";

/**
 * The platform terms (SPEC.md §18.5), written once per language. German is the
 * binding text; the other two are translations and say so.
 *
 * TERMS_VERSION is what a person accepts. The user service holds the version
 * in force (TERMS_VERSION in its environment) and refuses an acceptance of any
 * other, so a page showing older terms than the service enforces cannot record
 * consent to text nobody saw. Changing a word here means a new version here and
 * there, and everyone accepts again before their next publication.
 */
export const TERMS_VERSION = "2026-11-18";

/** The date of this version, as the page shows it. */
export const TERMS_AS_OF = "18.11.2026";

export interface TermsSection {
  heading: string;
  paragraphs: string[];
}

export const TERMS: Record<Locale, { title: string; note: string | null; sections: TermsSection[] }> = {
  de: {
    title: "Nutzungsregeln",
    note: null,
    sections: [
      {
        heading: "1. Worum es geht",
        paragraphs: [
          "wildro sammelt Outdoor-Events: Wanderungen, Läufe, Touren und Ausflüge. Mit einem Konto kannst du eigene Events veröffentlichen. Diese Regeln gelten für alles, was du hier veröffentlichst. Lesen geht ohne Konto und ohne diese Regeln.",
        ],
      },
      {
        heading: "2. Was du veröffentlichen darfst",
        paragraphs: [
          "Ein Event ist eine Unternehmung draußen, zu der andere dazukommen können: mit Datum, Uhrzeit und Treffpunkt. Wenn nicht jeder mitkommen kann, schreib dazu, wer mitkommen kann.",
          "Die Angaben müssen stimmen: Termin, Ort, Schwierigkeit und Kosten so, wie sie sind. Ändert sich etwas, änderst du das Event; fällt es aus, sagst du es ab.",
          "Schreib in deinen eigenen Worten. Kopiere keine Texte anderer und keine Angaben, die andere nicht öffentlich gemacht haben.",
          "Links führen nur zu Kanälen, die zu deinem Event oder deiner Gruppe gehören.",
        ],
      },
      {
        heading: "3. Was nicht erlaubt ist",
        paragraphs: [
          "Werbung für Waren oder Dienstleistungen ohne eine echte Unternehmung, Gewinnspiele, Kettenbriefe und wiederholte Einträge desselben Events.",
          "Namen, Telefonnummern, Adressen oder Fotos anderer Menschen ohne deren Einverständnis.",
          "Inhalte, die gegen geltendes Recht verstoßen, andere herabsetzen, bedrohen oder zu Gefahr anstiften, etwa Touren in gesperrte Gebiete.",
        ],
      },
      {
        heading: "4. Grenzen",
        paragraphs: [
          "Pro Konto höchstens 5 veröffentlichte Events innerhalb von 24 Stunden und höchstens 30 kommende Events gleichzeitig. Die Grenzen schützen vor Spam; wir können sie anpassen.",
        ],
      },
      {
        heading: "5. Meldungen und Entfernen",
        paragraphs: [
          "Jeder kann ein Event melden, auch ohne Konto. Wir prüfen Meldungen und entfernen Events, die gegen diese Regeln oder gegen Recht verstoßen. Eine Vorabprüfung gibt es nicht: Dein Event erscheint sofort, verantwortlich für den Inhalt bist du.",
          "Entfernen wir ein Event, siehst du in deinem Konto den Grund, und wir schreiben ihn dir an deine E-Mail-Adresse. Bist du anderer Meinung, antworte uns per E-Mail an die Adresse im Impressum. Wir prüfen die Entscheidung erneut und teilen dir das Ergebnis mit.",
          "Bei wiederholten oder schweren Verstößen können wir dein Konto sperren.",
        ],
      },
      {
        heading: "6. Dein Konto",
        paragraphs: [
          "Die Anmeldung läuft über unseren Dienstleister elvix. Dein Anzeigename steht öffentlich bei deinen Events, deine E-Mail-Adresse sieht niemand außer dir und uns. Du kannst dein Konto jederzeit selbst löschen; deine künftigen Events werden dann abgesagt. Mehr dazu in der Datenschutzerklärung.",
        ],
      },
      {
        heading: "7. Haftung",
        paragraphs: [
          "Wer an einem Event teilnimmt, tut das auf eigene Verantwortung und Gefahr. wildro veranstaltet die Events nicht und haftet nicht für ihren Ablauf. Für eigene Inhalte haften wir nach den gesetzlichen Vorschriften.",
        ],
      },
      {
        heading: "8. Änderungen",
        paragraphs: [
          "Ändern sich diese Regeln, fragen wir dich vor deiner nächsten Veröffentlichung erneut nach deiner Zustimmung. Bis dahin bleibt alles Veröffentlichte, wie es ist.",
        ],
      },
    ],
  },
  en: {
    title: "Terms of use",
    note: "This is a translation. The German text is the binding one.",
    sections: [
      {
        heading: "1. What this is about",
        paragraphs: [
          "wildro collects outdoor events: hikes, runs, tours and trips. With an account you can publish your own events. These terms apply to everything you publish here. Reading needs neither an account nor these terms.",
        ],
      },
      {
        heading: "2. What you may publish",
        paragraphs: [
          "An event is an outing that others can join, with a date, a time and a meeting point. If not everyone can join, say who can.",
          "The details must be true: date, place, difficulty and cost as they are. If something changes, edit the event; if it is called off, cancel it.",
          "Write in your own words. Do not copy other people's texts or details they have not made public.",
          "Links lead only to channels that belong to your event or your group.",
        ],
      },
      {
        heading: "3. What is not allowed",
        paragraphs: [
          "Advertising for goods or services without a real outing, prize draws, chain letters and repeated entries of the same event.",
          "Other people's names, phone numbers, addresses or photos without their consent.",
          "Content that breaks the law, demeans or threatens others or invites danger, such as tours into closed areas.",
        ],
      },
      {
        heading: "4. Limits",
        paragraphs: [
          "Per account at most 5 events published within 24 hours and at most 30 upcoming events at once. The limits protect against spam; we may adjust them.",
        ],
      },
      {
        heading: "5. Reports and removal",
        paragraphs: [
          "Anyone can report an event, without an account too. We review reports and remove events that break these terms or the law. There is no review in advance: your event appears at once, and you are responsible for its content.",
          "If we remove an event, your account shows the reason and we send it to your email address. If you disagree, reply to us at the address in the legal notice. We review the decision again and tell you the outcome.",
          "For repeated or serious breaches we may block your account.",
        ],
      },
      {
        heading: "6. Your account",
        paragraphs: [
          "Sign-in runs through our provider elvix. Your display name is shown publicly with your events; your email address is seen by nobody but you and us. You can delete your account yourself at any time; your upcoming events are then cancelled. More in the privacy policy.",
        ],
      },
      {
        heading: "7. Liability",
        paragraphs: [
          "Taking part in an event is at your own responsibility and risk. wildro does not run the events and is not liable for how they go. For our own content we are liable as the law provides.",
        ],
      },
      {
        heading: "8. Changes",
        paragraphs: [
          "If these terms change, we ask for your consent again before your next publication. Until then, everything published stays as it is.",
        ],
      },
    ],
  },
  ru: {
    title: "Правила площадки",
    note: "Это перевод. Юридическую силу имеет немецкий текст.",
    sections: [
      {
        heading: "1. О чём это",
        paragraphs: [
          "wildro собирает события на природе: походы, забеги, туры и вылазки. С аккаунтом можно публиковать свои события. Эти правила действуют для всего, что ты здесь публикуешь. Чтобы читать, не нужны ни аккаунт, ни правила.",
        ],
      },
      {
        heading: "2. Что можно публиковать",
        paragraphs: [
          "Событие это вылазка на природу, к которой могут присоединиться другие: с датой, временем и местом встречи. Если присоединиться может не каждый, напиши, кто может.",
          "Сведения должны быть правдой: дата, место, сложность и стоимость такими, какие они есть. Что-то изменилось, исправь событие; не состоится, отмени его.",
          "Пиши своими словами. Не копируй чужие тексты и сведения, которые другие не сделали публичными.",
          "Ссылки ведут только на каналы твоего события или твоей группы.",
        ],
      },
      {
        heading: "3. Что запрещено",
        paragraphs: [
          "Реклама товаров и услуг без настоящей вылазки, розыгрыши, письма счастья и повторные записи одного и того же события.",
          "Имена, телефоны, адреса и фотографии других людей без их согласия.",
          "Всё, что нарушает закон, унижает или угрожает другим или подталкивает к опасности, например походы в закрытые зоны.",
        ],
      },
      {
        heading: "4. Ограничения",
        paragraphs: [
          "На один аккаунт не больше 5 опубликованных событий за 24 часа и не больше 30 будущих событий одновременно. Ограничения защищают от спама, мы можем их менять.",
        ],
      },
      {
        heading: "5. Жалобы и снятие",
        paragraphs: [
          "Пожаловаться на событие может любой, и без аккаунта тоже. Мы проверяем жалобы и снимаем события, которые нарушают эти правила или закон. Предварительной проверки нет: событие появляется сразу, за его содержание отвечаешь ты.",
          "Если мы снимаем событие, причина видна в твоём аккаунте, и мы присылаем её на твою почту. Если ты с решением не согласен или не согласна, ответь нам на адрес из Impressum. Мы пересмотрим решение и сообщим результат.",
          "При повторных или серьёзных нарушениях мы можем заблокировать аккаунт.",
        ],
      },
      {
        heading: "6. Твой аккаунт",
        paragraphs: [
          "Вход работает через наш сервис elvix. Твоё имя видно у твоих событий, почту не видит никто, кроме тебя и нас. Удалить аккаунт можно в любой момент самому, будущие события при этом отменяются. Подробнее в Datenschutz.",
        ],
      },
      {
        heading: "7. Ответственность",
        paragraphs: [
          "Участие в событии под твою ответственность и на твой риск. wildro не проводит события и не отвечает за то, как они проходят. За собственное содержание мы отвечаем по закону.",
        ],
      },
      {
        heading: "8. Изменения",
        paragraphs: [
          "Если правила изменятся, перед следующей публикацией мы снова попросим твоё согласие. До тех пор всё опубликованное остаётся как есть.",
        ],
      },
    ],
  },
};
