package app.wildro.analytics.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** The four periods of the dashboard contract, each with its bucket size. */
enum Period {

	DAY("day", Duration.ofDays(1), "hour"), WEEK("week", Duration.ofDays(7), "day"),
	MONTH("month", Duration.ofDays(30), "day"), ALL("all", null, "month");

	final String wire;

	private final Duration length;

	/** Unit for {@code date_trunc}, a fixed literal and never user input. */
	final String bucket;

	Period(String wire, Duration length, String bucket) {
		this.wire = wire;
		this.length = length;
		this.bucket = bucket;
	}

	Optional<Instant> since(Instant now) {
		return this.length == null ? Optional.empty() : Optional.of(now.minus(this.length));
	}

	/** Anything unknown falls back to a month, as the other projects on the dashboard do. */
	static Period parse(String value) {
		for (Period period : values()) {
			if (period.wire.equals(value)) {
				return period;
			}
		}
		return MONTH;
	}

}
