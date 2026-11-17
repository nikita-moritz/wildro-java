package app.wildro.analytics.track;

import java.util.regex.Pattern;

/**
 * Crawlers, link previews and automation, by user agent. {@code headless} is in
 * the list because our own browser checks against the live site would otherwise
 * count as audience. No bare {@code preview}: it appears in real browser strings.
 */
final class BotFilter {

	private static final Pattern BOTS = Pattern.compile(
			"bot|crawl|spider|slurp|googlebot|bingbot|yandex|baidu|duckduck|facebookexternalhit|twitterbot"
					+ "|linkedinbot|telegrambot|whatsapp|lighthouse|headless",
			Pattern.CASE_INSENSITIVE);

	private BotFilter() {
	}

	static boolean isBot(String userAgent) {
		return userAgent == null || userAgent.isBlank() || BOTS.matcher(userAgent).find();
	}

}
