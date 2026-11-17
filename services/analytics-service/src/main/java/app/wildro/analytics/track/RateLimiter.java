package app.wildro.analytics.track;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import app.wildro.analytics.AnalyticsProperties;

/**
 * At most N views per client address and minute (SPEC.md §13), counted in a
 * fixed one-minute window.
 * <p>
 * In memory and nowhere else: the address is needed to count, never to store.
 * A restart forgets every count, which errs towards recording a few too many
 * views rather than towards keeping addresses. The map is cleared whenever the
 * minute changes, so it holds at most one minute of addresses.
 */
@Component
class RateLimiter {

	private final Clock clock;

	private final int perMinute;

	private final Map<String, Integer> counts = new ConcurrentHashMap<>();

	private volatile long minute = -1;

	RateLimiter(Clock clock, AnalyticsProperties properties) {
		this.clock = clock;
		this.perMinute = properties.rateLimitPerMinute();
	}

	boolean allow(String address) {
		long now = this.clock.millis() / 60_000;
		if (now != this.minute) {
			synchronized (this) {
				if (now != this.minute) {
					this.counts.clear();
					this.minute = now;
				}
			}
		}
		return this.counts.merge(address, 1, Integer::sum) <= this.perMinute;
	}

}
