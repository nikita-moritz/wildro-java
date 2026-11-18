package app.wildro.user;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what. The service listens on loopback and its only caller is the
 * frontend's server, but every account route still demands an elvix JWT: the
 * subject of a verified token is the only way a request names a person, never a
 * parameter or a header the caller could choose.
 *
 * The webhook is open at this layer because elvix cannot present a token; its
 * HMAC signature is checked in the controller instead. Anything not listed is
 * refused, so a new endpoint is closed until someone opens it on purpose.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((requests) -> requests
			.requestMatchers(HttpMethod.GET, "/", "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
			.permitAll()
			.requestMatchers(HttpMethod.POST, "/v1/webhooks/elvix")
			.permitAll()
			.requestMatchers("/v1/me", "/v1/me/**")
			.authenticated()
			.anyRequest()
			.denyAll())
			.oauth2ResourceServer((server) -> server.jwt(Customizer.withDefaults()))
			// Bearer tokens only, no cookie ever reaches this service, so there is
			// no ambient credential for a cross-site request to ride on.
			.csrf((csrf) -> csrf.disable())
			.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

}
