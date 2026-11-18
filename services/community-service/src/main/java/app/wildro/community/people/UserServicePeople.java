package app.wildro.community.people;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@code GET /v1/me} on the user service, with the caller's own token passed on.
 * The user service verifies it again for itself; nothing here vouches for the
 * person on its own authority, and no service-to-service secret exists to leak.
 */
@Component
class UserServicePeople implements PeopleDirectory {

	private final RestClient client;

	private final boolean configured;

	UserServicePeople(RestClient.Builder builder, @Value("${wildro.users.base-url:}") String baseUrl) {
		this.configured = !baseUrl.isBlank();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
		factory.setReadTimeout(Duration.ofSeconds(8));
		this.client = builder.baseUrl(this.configured ? baseUrl : "http://127.0.0.1:1").requestFactory(factory).build();
	}

	@Override
	public Person whoIs(Jwt token) {
		if (!this.configured) {
			throw new PeopleUnavailableException("no user service configured", null);
		}
		try {
			Me me = this.client.get()
				.uri("/v1/me")
				.header("Authorization", "Bearer " + token.getTokenValue())
				.retrieve()
				.body(Me.class);
			if (me == null || me.id() == null || !me.id().equals(token.getSubject())) {
				throw new PeopleUnavailableException("the user service answered for someone else", null);
			}
			return new Person(me.id(), me.displayName(), me.terms() != null && me.terms().upToDate());
		}
		catch (RestClientException ex) {
			throw new PeopleUnavailableException("the user service did not answer", ex);
		}
	}

	record Me(String id, String displayName, Terms terms) {
	}

	record Terms(boolean upToDate) {
	}

}
