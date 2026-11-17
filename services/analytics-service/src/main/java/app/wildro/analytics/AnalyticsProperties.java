package app.wildro.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The settings of SPEC.md §13 in one place.
 *
 * @param siteHost the only host whose traffic counts, {@code wildro.app}. Any
 *        other host (the dev site, a preview, a local run) is accepted and
 *        dropped, so our own work never shows up as audience.
 * @param salt input of the daily visitor hash. Blank means no hash is written
 *        at all and the visitor metrics are reported as unknown, never as zero.
 * @param disabled drop everything, set on dev in addition to the host rule.
 * @param rateLimitPerMinute views accepted per client address and minute.
 * @param retentionMonths raw rows older than this are deleted.
 */
@ConfigurationProperties("analytics")
public record AnalyticsProperties(String siteHost, String salt, boolean disabled, int rateLimitPerMinute,
		int retentionMonths) {

	public boolean hashing() {
		return this.salt != null && !this.salt.isBlank();
	}

}
