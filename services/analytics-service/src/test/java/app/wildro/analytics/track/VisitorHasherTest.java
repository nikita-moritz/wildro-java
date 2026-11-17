package app.wildro.analytics.track;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import app.wildro.analytics.AnalyticsProperties;
import app.wildro.analytics.MutableClock;

/** The cookieless identity of SPEC.md §13, without Spring. */
class VisitorHasherTest {

	private final MutableClock clock = new MutableClock();

	private final VisitorHasher hasher = new VisitorHasher(this.clock,
			new AnalyticsProperties("wildro.app", "salt", false, 60, 14));

	@Test
	void theSameVisitorOnTheSameDayHasOneHash() {
		this.clock.set(Instant.parse("2026-10-21T08:00:00Z"));
		String morning = this.hasher.hash("wildro.app", "203.0.113.7", "UA").orElseThrow();
		this.clock.set(Instant.parse("2026-10-21T22:00:00Z"));
		String evening = this.hasher.hash("wildro.app", "203.0.113.7", "UA").orElseThrow();

		assertThat(evening).isEqualTo(morning).matches("[0-9a-f]{32}");
	}

	@Test
	void theNextDayTheSameVisitorIsSomeoneElse() {
		this.clock.set(Instant.parse("2026-10-21T23:59:00Z"));
		String today = this.hasher.hash("wildro.app", "203.0.113.7", "UA").orElseThrow();
		this.clock.advance(Duration.ofMinutes(2));
		String tomorrow = this.hasher.hash("wildro.app", "203.0.113.7", "UA").orElseThrow();

		assertThat(tomorrow).isNotEqualTo(today);
	}

	@Test
	void anotherBrowserOrAddressIsAnotherVisitor() {
		String one = this.hasher.hash("wildro.app", "203.0.113.7", "UA").orElseThrow();

		assertThat(this.hasher.hash("wildro.app", "203.0.113.7", "other UA")).hasValueSatisfying(
				other -> assertThat(other).isNotEqualTo(one));
		assertThat(this.hasher.hash("wildro.app", "203.0.113.8", "UA")).hasValueSatisfying(
				other -> assertThat(other).isNotEqualTo(one));
	}

	@Test
	void withoutASaltOrAnAddressThereIsNoHash() {
		VisitorHasher unsalted = new VisitorHasher(this.clock, new AnalyticsProperties("wildro.app", "", false, 60, 14));

		assertThat(unsalted.hash("wildro.app", "203.0.113.7", "UA")).isEmpty();
		assertThat(this.hasher.hash("wildro.app", "", "UA")).isEmpty();
	}

}
