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

	/** The same statement for a group: written in the group's language, sent to its owner. */
	static Mail communitySuspended(String locale, String communityName, String termsUrl, String reason) {
		return switch (locale == null ? "de" : locale) {
			case "en" -> new Mail("Your group on wildro was removed: " + communityName, """
					Hello,

					we have removed your group "%s" from wildro. It is no longer shown in the list of groups or at its address, and nobody can join it.

					Reason:
					%s

					The decision rests on our terms of use: %s
					It was taken by a person from our moderation, not by an automated system.

					If you disagree, reply to this email. We will review the decision again and tell you the outcome.

					wildro
					""".formatted(communityName, reason, termsUrl));
			case "ru" -> new Mail("Твоя группа на wildro снята: " + communityName, """
					Здравствуй,

					мы сняли твою группу «%s» с wildro. Её больше нет в списке групп и по её адресу, вступить в неё нельзя.

					Причина:
					%s

					Решение основано на правилах площадки: %s
					Его принял человек из модерации, а не автоматическая система.

					Если ты с решением не согласен или не согласна, ответь на это письмо. Мы пересмотрим решение и сообщим результат.

					wildro
					""".formatted(communityName, reason, termsUrl));
			default -> new Mail("Deine Gruppe auf wildro wurde entfernt: " + communityName, """
					Hallo,

					wir haben deine Gruppe „%s“ von wildro entfernt. Sie erscheint nicht mehr in der Liste der Gruppen und unter ihrer Adresse, und niemand kann ihr beitreten.

					Grund:
					%s

					Die Entscheidung stützt sich auf unsere Nutzungsregeln: %s
					Getroffen hat sie ein Mensch aus unserer Moderation, kein automatisches System.

					Bist du anderer Meinung, antworte auf diese E-Mail. Wir prüfen die Entscheidung erneut und teilen dir das Ergebnis mit.

					wildro
					""".formatted(communityName, reason, termsUrl));
		};
	}

	static Mail communityLogoRemoved(String locale, String communityName, String termsUrl, String reason) {
		return switch (locale == null ? "de" : locale) {
			case "en" -> new Mail("The picture of your group on wildro was removed: " + communityName, """
					Hello,

					we have removed the picture of your group "%s" on wildro. The group itself stays as it is; you can upload another picture in its settings.

					Reason:
					%s

					The decision rests on our terms of use: %s
					It was taken by a person from our moderation, not by an automated system.

					If you disagree, reply to this email. We will review the decision again and tell you the outcome.

					wildro
					""".formatted(communityName, reason, termsUrl));
			case "ru" -> new Mail("Картинка твоей группы на wildro снята: " + communityName, """
					Здравствуй,

					мы сняли картинку твоей группы «%s» на wildro. Сама группа осталась как была, новую картинку можно загрузить в её настройках.

					Причина:
					%s

					Решение основано на правилах площадки: %s
					Его принял человек из модерации, а не автоматическая система.

					Если ты с решением не согласен или не согласна, ответь на это письмо. Мы пересмотрим решение и сообщим результат.

					wildro
					""".formatted(communityName, reason, termsUrl));
			default -> new Mail("Das Bild deiner Gruppe auf wildro wurde entfernt: " + communityName, """
					Hallo,

					wir haben das Bild deiner Gruppe „%s“ auf wildro entfernt. Die Gruppe selbst bleibt, wie sie ist; ein anderes Bild kannst du in ihren Einstellungen hochladen.

					Grund:
					%s

					Die Entscheidung stützt sich auf unsere Nutzungsregeln: %s
					Getroffen hat sie ein Mensch aus unserer Moderation, kein automatisches System.

					Bist du anderer Meinung, antworte auf diese E-Mail. Wir prüfen die Entscheidung erneut und teilen dir das Ergebnis mit.

					wildro
					""".formatted(communityName, reason, termsUrl));
		};
	}

	static Mail coverRemoved(String locale, String eventName, String termsUrl, String reason) {
		return switch (locale == null ? "de" : locale) {
			case "en" -> new Mail("The picture of your event on wildro was removed: " + eventName, """
					Hello,

					we have removed the picture you uploaded for your event "%s" on wildro. The event itself stays as it is and now shows a stand-in picture; you can upload another one in your account.

					Reason:
					%s

					The decision rests on our terms of use: %s
					It was taken by a person from our moderation, not by an automated system.

					If you disagree, reply to this email. We will review the decision again and tell you the outcome.

					wildro
					""".formatted(eventName, reason, termsUrl));
			case "ru" -> new Mail("Картинка твоего события на wildro снята: " + eventName, """
					Здравствуй,

					мы сняли картинку, которую ты загрузил или загрузила к событию «%s» на wildro. Само событие осталось как было и теперь показывает картинку по умолчанию, новую можно загрузить в аккаунте.

					Причина:
					%s

					Решение основано на правилах площадки: %s
					Его принял человек из модерации, а не автоматическая система.

					Если ты с решением не согласен или не согласна, ответь на это письмо. Мы пересмотрим решение и сообщим результат.

					wildro
					""".formatted(eventName, reason, termsUrl));
			default -> new Mail("Das Bild deines Events auf wildro wurde entfernt: " + eventName, """
					Hallo,

					wir haben das Bild entfernt, das du für dein Event „%s“ auf wildro hochgeladen hast. Das Event selbst bleibt, wie es ist, und zeigt jetzt ein Symbolbild; ein anderes Bild kannst du in deinem Konto hochladen.

					Grund:
					%s

					Die Entscheidung stützt sich auf unsere Nutzungsregeln: %s
					Getroffen hat sie ein Mensch aus unserer Moderation, kein automatisches System.

					Bist du anderer Meinung, antworte auf diese E-Mail. Wir prüfen die Entscheidung erneut und teilen dir das Ergebnis mit.

					wildro
					""".formatted(eventName, reason, termsUrl));
		};
	}

}
