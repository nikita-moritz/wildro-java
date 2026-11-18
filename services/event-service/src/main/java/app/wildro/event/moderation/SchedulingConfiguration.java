package app.wildro.event.moderation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Housekeeping runs in the service only. The command line starts this same jar
 * without a web server for one command and must not pick up timers.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@EnableScheduling
class SchedulingConfiguration {

}
