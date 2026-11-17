package app.wildro.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The one clock the service reads. Injected rather than called statically so a
 * test can pin "now" and check the boundary of "has this event passed" instead
 * of hoping the suite does not run at midnight.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
