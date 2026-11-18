package app.wildro.event;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what. Reading stays open, exactly as on stage one. Writing own
 * events and moderating demand an elvix JWT; filing a report does not, a guest
 * may report (SPEC.md §18.2). Anything not listed is refused.
 *
 * The decoder is built here rather than from properties so that a missing
 * clientId refuses every token instead of either failing the start (the
 * command line runs this same jar and needs no token) or, worse, accepting
 * tokens for any elvix application: one issuer serves them all, and the
 * audience is the whole tenant boundary.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((requests) -> requests
			.requestMatchers(HttpMethod.GET, "/", "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
					"/v1/events", "/v1/events/**")
			.permitAll()
			.requestMatchers(HttpMethod.POST, "/v1/reports")
			.permitAll()
			.requestMatchers("/v1/own/**")
			.authenticated()
			.requestMatchers("/v1/moderation/**")
			.hasAnyRole("moderator", "superuser")
			.anyRequest()
			.denyAll())
			.oauth2ResourceServer((server) -> server.jwt(Customizer.withDefaults()))
			.csrf((csrf) -> csrf.disable())
			.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${elvix.issuer}") String issuer, @Value("${elvix.client-id:}") String clientId) {
		if (clientId.isBlank()) {
			return (token) -> {
				throw new BadJwtException("no elvix clientId configured, no token is accepted");
			};
		}
		// Resolved on first use, so starting the service never waits on elvix.
		return new SupplierJwtDecoder(() -> {
			NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
			decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
					new JwtClaimValidator<List<String>>("aud", (aud) -> aud != null && aud.contains(clientId))));
			return decoder;
		});
	}

	/** Role slugs from elvix's {@code roles} claim become {@code ROLE_moderator} and so on. */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName("roles");
		roles.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(roles);
		return converter;
	}

}
