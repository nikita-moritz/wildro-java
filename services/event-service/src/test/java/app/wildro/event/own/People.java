package app.wildro.event.own;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Verified elvix tokens for tests: a subject and the authorities its roles claim maps to. */
public final class People {

	private People() {
	}

	public static JwtRequestPostProcessor person(String subject, String... roles) {
		List<String> all = roles.length == 0 ? List.of("user") : List.of(roles);
		return jwt().jwt((token) -> token.subject(subject).claim("roles", all))
			.authorities(all.stream().<GrantedAuthority>map((role) -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
	}

	public static JwtRequestPostProcessor moderator(String subject) {
		return person(subject, Arrays.asList("user", "moderator").toArray(String[]::new));
	}

}
