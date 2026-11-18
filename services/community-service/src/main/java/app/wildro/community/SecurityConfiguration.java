package app.wildro.community;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The list and the page of a community are public; standing, members and every
 * change need an elvix JWT; moderation needs the superuser role (§18.4); the
 * outbox is for the other services, behind the shared token. Anything else is
 * refused. Order matters: the narrower paths come first.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@EnableScheduling
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((requests) -> requests
			.requestMatchers(HttpMethod.GET, "/", "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
			.permitAll()
			.requestMatchers(HttpMethod.GET, "/internal/outbox")
			.permitAll()
			.requestMatchers(HttpMethod.GET, "/v1/communities/*/me", "/v1/communities/*/members")
			.authenticated()
			.requestMatchers(HttpMethod.GET, "/v1/communities", "/v1/communities/*")
			.permitAll()
			.requestMatchers("/v1/communities", "/v1/communities/**", "/v1/me/communities")
			.authenticated()
			.requestMatchers("/v1/moderation/**")
			.hasRole("superuser")
			.anyRequest()
			.denyAll())
			.oauth2ResourceServer((server) -> server.jwt(Customizer.withDefaults()))
			.csrf((csrf) -> csrf.disable())
			.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

}
