package app.wildro.event.own;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@code GET /v1/communities/{slug}/me} on the community service, with the
 * caller's own token. "Was an admin yesterday" does not count: this is asked on
 * every write in a community's name.
 */
@Component
class CommunityServiceGateway implements CommunityGateway {

	private final RestClient client;

	private final boolean configured;

	CommunityServiceGateway(RestClient.Builder builder, @Value("${wildro.communities.base-url:}") String baseUrl) {
		this.configured = !baseUrl.isBlank();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
		factory.setReadTimeout(Duration.ofSeconds(8));
		this.client = builder.baseUrl(this.configured ? baseUrl : "http://127.0.0.1:1").requestFactory(factory).build();
	}

	@Override
	public Standing standing(Jwt token, String slug) {
		if (!this.configured) {
			throw new WriterUnavailableException("no community service configured", null);
		}
		try {
			return this.client.get()
				.uri("/v1/communities/{slug}/me", slug)
				.header("Authorization", "Bearer " + token.getTokenValue())
				.retrieve()
				.onStatus((status) -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
				})
				.body(Standing.class);
		}
		catch (RestClientException ex) {
			throw new WriterUnavailableException("the community service did not answer", ex);
		}
	}

}
