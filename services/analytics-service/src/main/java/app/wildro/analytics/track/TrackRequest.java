package app.wildro.analytics.track;

/**
 * One page view as the frontend forwards it. The frontend is the only caller:
 * the service listens on loopback, so the browser cannot reach it and cannot
 * claim a host or an address of its choosing. Host, address and user agent are
 * what the frontend itself received.
 * <p>
 * Every field may be missing or malformed. The beacon has no user to tell, so a
 * bad request is dropped, not refused (SPEC.md §13).
 */
public record TrackRequest(String path, String referrer, String utmSource, String utmMedium, String utmCampaign,
		String host, String clientIp, String userAgent) {
}
