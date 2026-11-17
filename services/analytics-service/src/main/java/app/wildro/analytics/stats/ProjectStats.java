package app.wildro.analytics.stats;

import java.util.List;

/**
 * The stats contract every project on the nikone.dev owner dashboard answers
 * (lib/project-stats/contract.ts there). Anything that cannot be measured is
 * null, never zero, so the dashboard draws a dash instead of a false number.
 */
public record ProjectStats(String project, String period, String generatedAt, Traffic traffic,
		List<Metric> business, List<Count> topPages, List<Count> topSources) {

	public record Traffic(long views, long totalViews, Long uniques, Long sessions, Double bounceRate,
			Double avgDurationSec, List<Point> series) {
	}

	public record Point(String date, long views, Long uniques) {
	}

	public record Metric(String key, String label, long value) {
	}

	public record Count(String name, long count) {
	}

}
