package app.wildro.user;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One clock for the terms mark and the webhook replay window, replaceable in tests. */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
