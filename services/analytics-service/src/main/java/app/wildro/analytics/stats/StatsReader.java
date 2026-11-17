package app.wildro.analytics.stats;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import app.wildro.analytics.AnalyticsProperties;
import app.wildro.analytics.stats.ProjectStats.Count;
import app.wildro.analytics.stats.ProjectStats.Metric;
import app.wildro.analytics.stats.ProjectStats.Point;
import app.wildro.analytics.stats.ProjectStats.Traffic;

/**
 * Aggregates for one period. Plain SQL on purpose: sessions need a window
 * function and the source needs a CASE over two columns, neither of which an
 * ORM expresses better than the query itself.
 */
@Component
class StatsReader {

	private static final Map<String, String> LOCALE_LABELS = Map.of("de", "Deutsch", "en", "English", "ru",
			"Русский");

	/**
	 * Where a visit came from: an explicit campaign first, otherwise the
	 * referrer's host, and a referrer on this site is navigation, not a source.
	 * Computed when reading, so the raw columns stay the truth and a changed rule
	 * re-reads history instead of losing it.
	 */
	private static final String SOURCE = """
			CASE
			  WHEN utm_source IS NOT NULL THEN utm_source
			  WHEN referrer IS NULL THEN 'direct'
			  WHEN split_part(split_part(referrer, '//', 2), '/', 1) = :siteHost THEN 'direct'
			  ELSE split_part(split_part(referrer, '//', 2), '/', 1)
			END""";

	private final JdbcClient jdbc;

	private final Clock clock;

	private final AnalyticsProperties properties;

	StatsReader(JdbcClient jdbc, Clock clock, AnalyticsProperties properties) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.properties = properties;
	}

	ProjectStats read(Period period) {
		Instant now = this.clock.instant();
		// No lower bound for "all": the epoch keeps every query one shape.
		Timestamp since = Timestamp.from(period.since(now).orElse(Instant.EPOCH));

		long total = this.jdbc.sql("SELECT count(*) FROM page_view").query(Long.class).single();
		long views = this.jdbc.sql("SELECT count(*) FROM page_view WHERE viewed_at >= :since")
			.param("since", since)
			.query(Long.class)
			.single();
		long hashed = this.jdbc.sql("SELECT count(visitor_hash) FROM page_view WHERE viewed_at >= :since")
			.param("since", since)
			.query(Long.class)
			.single();
		// Without hashes the visitor metrics are unknowable, not zero.
		boolean measured = hashed > 0;

		Long uniques = measured ? this.jdbc
			.sql("SELECT count(DISTINCT visitor_hash) FROM page_view WHERE viewed_at >= :since")
			.param("since", since)
			.query(Long.class)
			.single() : null;

		List<Point> series = this.jdbc.sql("""
				SELECT date_trunc('%s', viewed_at) AS bucket, count(*) AS views, count(DISTINCT visitor_hash) AS uniques
				FROM page_view WHERE viewed_at >= :since
				GROUP BY bucket ORDER BY bucket
				""".formatted(period.bucket))
			.param("since", since)
			.query((rs, row) -> new Point(rs.getTimestamp("bucket").toInstant().toString(), rs.getLong("views"),
					measured ? rs.getLong("uniques") : null))
			.list();

		List<Count> pages = counts("""
				SELECT path AS name, count(*) AS n FROM page_view WHERE viewed_at >= :since
				GROUP BY path ORDER BY n DESC, path LIMIT 10""", since);
		List<Count> sources = counts("SELECT " + SOURCE + """
				 AS name, count(*) AS n FROM page_view WHERE viewed_at >= :since
				GROUP BY name ORDER BY n DESC, name LIMIT 10""", since);
		List<Metric> languages = this.jdbc.sql("""
				SELECT locale, count(*) AS n FROM page_view WHERE viewed_at >= :since AND locale IS NOT NULL
				GROUP BY locale ORDER BY n DESC, locale""")
			.param("since", since)
			.query((rs, row) -> new Metric("locale_" + rs.getString("locale"),
					LOCALE_LABELS.getOrDefault(rs.getString("locale"), rs.getString("locale")), rs.getLong("n")))
			.list();

		Long sessions = null;
		Double bounceRate = null;
		Double avgDuration = null;
		if (measured) {
			Map<String, Object> session = this.jdbc.sql("""
					WITH marked AS (
					  SELECT visitor_hash, viewed_at,
					         CASE WHEN lag(viewed_at) OVER w IS NULL
					                OR viewed_at - lag(viewed_at) OVER w > interval '30 minutes'
					              THEN 1 ELSE 0 END AS is_new
					  FROM page_view
					  WHERE visitor_hash IS NOT NULL AND viewed_at >= :since
					  WINDOW w AS (PARTITION BY visitor_hash ORDER BY viewed_at)
					), numbered AS (
					  SELECT visitor_hash, viewed_at,
					         sum(is_new) OVER (PARTITION BY visitor_hash ORDER BY viewed_at
					                           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS session_no
					  FROM marked
					), per_session AS (
					  SELECT count(*) AS views, extract(epoch FROM max(viewed_at) - min(viewed_at)) AS seconds
					  FROM numbered GROUP BY visitor_hash, session_no
					)
					SELECT count(*) AS sessions,
					       sum(CASE WHEN views = 1 THEN 1 ELSE 0 END)::float8 / nullif(count(*), 0) AS bounce,
					       avg(seconds)::float8 AS duration
					FROM per_session""")
				.param("since", since)
				.query()
				.singleRow();
			sessions = ((Number) session.get("sessions")).longValue();
			bounceRate = (Double) session.get("bounce");
			avgDuration = (Double) session.get("duration");
		}

		Traffic traffic = new Traffic(views, total, uniques, sessions, bounceRate, avgDuration, series);
		return new ProjectStats("wildro", period.wire, now.toString(), traffic, languages, pages, sources);
	}

	private List<Count> counts(String sql, Timestamp since) {
		return this.jdbc.sql(sql)
			.param("since", since)
			.param("siteHost", this.properties.siteHost())
			.query((rs, row) -> new Count(rs.getString("name"), rs.getLong("n")))
			.list();
	}

}
