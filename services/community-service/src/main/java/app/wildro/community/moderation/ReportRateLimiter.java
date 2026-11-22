package app.wildro.community.moderation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * At most N reports per address and hour, as for events (SPEC.md §18.2). In
 * memory, so no address is ever written to the database: one instance serves
 * the site, and a restart forgetting the window costs at most an hour's worth.
 * The event service keeps its own window; each service is reached on its own.
 */
@Component
class ReportRateLimiter {

	private static final Duration WINDOW = Duration.ofHours(1);

	private final Map<String, Deque<Instant>> seen = new ConcurrentHashMap<>();

	private final int perHour;

	private final Clock clock;

	ReportRateLimiter(@Value("${wildro.reports.per-hour-per-address}") int perHour, Clock clock) {
		this.perHour = perHour;
		this.clock = clock;
	}

	/** Counts this report if there is room for it. */
	boolean tryAcquire(String address) {
		Instant now = this.clock.instant();
		Deque<Instant> times = this.seen.computeIfAbsent(address == null ? "" : address, (key) -> new ArrayDeque<>());
		synchronized (times) {
			while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) {
				times.pollFirst();
			}
			if (times.size() >= this.perHour) {
				return false;
			}
			times.addLast(now);
			return true;
		}
	}

	@Scheduled(fixedDelay = 15 * 60 * 1000)
	void forgetQuietAddresses() {
		Instant cutoff = this.clock.instant().minus(WINDOW);
		this.seen.entrySet().removeIf((entry) -> {
			synchronized (entry.getValue()) {
				return entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff);
			}
		});
	}

	void reset() {
		this.seen.clear();
	}

}
