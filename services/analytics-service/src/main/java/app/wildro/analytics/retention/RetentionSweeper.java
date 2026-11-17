package app.wildro.analytics.retention;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import app.wildro.analytics.AnalyticsProperties;

/**
 * Deletes raw rows older than the retention period, SPEC.md §13 and §17. Daily
 * rather than on every write: the time index makes the delete cheap, and a
 * schedule cannot silently stop the way a "sometimes on write" sweep can when
 * traffic does.
 */
@Component
public class RetentionSweeper {

	private static final Logger log = LoggerFactory.getLogger(RetentionSweeper.class);

	private final JdbcClient jdbc;

	private final Clock clock;

	private final int months;

	RetentionSweeper(JdbcClient jdbc, Clock clock, AnalyticsProperties properties) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.months = properties.retentionMonths();
	}

	@Scheduled(cron = "0 17 3 * * *", zone = "UTC")
	public int sweep() {
		ZonedDateTime cutoff = ZonedDateTime.now(this.clock.withZone(ZoneOffset.UTC)).minusMonths(this.months);
		int deleted = this.jdbc.sql("DELETE FROM page_view WHERE viewed_at < :cutoff")
			.param("cutoff", Timestamp.from(cutoff.toInstant()))
			.update();
		if (deleted > 0) {
			log.info("retention: deleted {} page views older than {}", deleted, cutoff);
		}
		return deleted;
	}

}
