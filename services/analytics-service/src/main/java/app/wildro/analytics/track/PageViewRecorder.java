package app.wildro.analytics.track;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import app.wildro.analytics.AnalyticsProperties;

/**
 * Decides whether a view counts and stores it. Every refusal is silent: the
 * caller is a beacon (SPEC.md §13).
 */
@Component
class PageViewRecorder {

	private static final Logger log = LoggerFactory.getLogger(PageViewRecorder.class);

	private static final Set<String> LOCALES = Set.of("de", "en", "ru");

	private final AnalyticsProperties properties;

	private final RateLimiter rateLimiter;

	private final VisitorHasher hasher;

	private final JdbcClient jdbc;

	PageViewRecorder(AnalyticsProperties properties, RateLimiter rateLimiter, VisitorHasher hasher, JdbcClient jdbc) {
		this.properties = properties;
		this.rateLimiter = rateLimiter;
		this.hasher = hasher;
		this.jdbc = jdbc;
	}

	/** @return whether a row was written, for tests and metrics; the endpoint ignores it. */
	boolean record(TrackRequest view) {
		if (this.properties.disabled() || view == null) {
			return false;
		}
		String host = normaliseHost(view.host());
		// A preview, the dev site or a local run is our own work, not audience.
		if (host == null || !host.equals(this.properties.siteHost())) {
			return false;
		}
		String path = clip(view.path(), 500);
		if (path == null || !path.startsWith("/")) {
			return false;
		}
		if (BotFilter.isBot(view.userAgent())) {
			return false;
		}
		String address = view.clientIp() == null ? "" : view.clientIp().trim();
		if (!address.isEmpty() && !this.rateLimiter.allow(address)) {
			return false;
		}
		Optional<String> visitor = this.hasher.hash(host, address, view.userAgent());
		try {
			this.jdbc.sql("""
					INSERT INTO page_view (path, locale, host, referrer, utm_source, utm_medium, utm_campaign, visitor_hash)
					VALUES (:path, :locale, :host, :referrer, :utmSource, :utmMedium, :utmCampaign, :visitor)
					""")
				.param("path", path)
				.param("locale", localeOf(path))
				.param("host", host)
				.param("referrer", clip(view.referrer(), 1000))
				.param("utmSource", clip(view.utmSource(), 100))
				.param("utmMedium", clip(view.utmMedium(), 100))
				.param("utmCampaign", clip(view.utmCampaign(), 100))
				.param("visitor", visitor.orElse(null))
				.update();
			return true;
		}
		catch (DataAccessException ex) {
			log.warn("page view not stored: {}", ex.getMostSpecificCause().getMessage());
			return false;
		}
	}

	/** The language is the first path segment: every page of the site lives under /de, /en or /ru. */
	static String localeOf(String path) {
		int end = path.indexOf('/', 1);
		String first = end < 0 ? path.substring(1) : path.substring(1, end);
		return LOCALES.contains(first) ? first : null;
	}

	/** Host header form: lower case, no port. */
	static String normaliseHost(String host) {
		if (host == null || host.isBlank()) {
			return null;
		}
		String plain = host.trim().toLowerCase(Locale.ROOT);
		int colon = plain.indexOf(':');
		return colon < 0 ? plain : plain.substring(0, colon);
	}

	private static String clip(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

}
