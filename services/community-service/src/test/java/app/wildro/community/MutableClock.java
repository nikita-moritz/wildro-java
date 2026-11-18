package app.wildro.community;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A UTC clock that stands still until a test moves it. */
public class MutableClock extends Clock {

	private volatile Instant now = Instant.parse("2026-11-18T10:00:00Z");

	public void set(Instant instant) {
		this.now = instant;
	}

	public void advance(Duration duration) {
		this.now = this.now.plus(duration);
	}

	@Override
	public Instant instant() {
		return this.now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}
