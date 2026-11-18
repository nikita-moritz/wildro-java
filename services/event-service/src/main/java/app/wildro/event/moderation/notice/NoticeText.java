package app.wildro.event.moderation.notice;

/**
 * The statement of reasons (DSA Art. 17) in the language the event was written
 * in: what was decided, about what, why, on which basis, and how to contest it.
 */
final class NoticeText {

	private NoticeText() {
	}

	record Mail(String subject, String text) {
	}

	static Mail suspended(String locale, String eventName, String termsUrl, String reason) {
		return switch (locale == null ? "de" : locale) {
			case "en" -> new Mail("Your event on wildro was removed: " + eventName, """
					Hello,

					we have removed your event "%s" from wildro. It is no longer shown in the list, on the map or at its address. You can still see it in your account.

					Reason:
					%s

					The decision rests on our terms of use: %s
					It was taken by a person after a report, not by an automated system.

					If you disagree, reply to this email. We will review the decision again and tell you the outcome.

					wildro
					""".formatted(eventName, reason, termsUrl));
			case "ru" -> new Mail("Твоё событие на wildro снято: " + eventName, """
					Здравствуй,

					мы сняли твоё событие «%s» с wildro. Его больше нет в ленте, на карте и по его адресу. В аккаунте оно по-прежнему видно.

					Причина:
					%s

					Решение основано на правилах площадки: %s
					Его принял человек после жалобы, а не автоматическая система.

					Если ты с решением не согласен или не согласна, ответь на это письмо. Мы пересмотрим решение и сообщим результат.

					wildro
					""".formatted(eventName, reason, termsUrl));
			default -> new Mail("Dein Event auf wildro wurde entfernt: " + eventName, """
					Hallo,

					wir haben dein Event „%s“ von wildro entfernt. Es erscheint nicht mehr in der Liste, auf der Karte und unter seiner Adresse. In deinem Konto siehst du es weiterhin.

					Grund:
					%s

					Die Entscheidung stützt sich auf unsere Nutzungsregeln: %s
					Getroffen hat sie ein Mensch nach einer Meldung, kein automatisches System.

					Bist du anderer Meinung, antworte auf diese E-Mail. Wir prüfen die Entscheidung erneut und teilen dir das Ergebnis mit.

					wildro
					""".formatted(eventName, reason, termsUrl));
		};
	}

}
