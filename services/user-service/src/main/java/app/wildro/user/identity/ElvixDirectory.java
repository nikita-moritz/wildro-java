package app.wildro.user.identity;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import app.wildro.user.ElvixProperties;

/**
 * The management API's member lookup, {@code GET /api/applications/{id}/users/{uid}}.
 * Called once per person, when they first arrive, and again only when elvix
 * says their address changed. It is never on the path of an ordinary request.
 */
@Component
class ElvixDirectory implements IdentityDirectory {

	private final RestClient client;

	private final String applicationId;

	ElvixDirectory(RestClient.Builder builder, ElvixProperties elvix) {
		this.applicationId = elvix.applicationId();
		this.client = builder.baseUrl(elvix.baseUrl())
			.defaultHeader("Authorization", "Bearer " + elvix.apiKey())
			.requestFactory(requestFactory())
			.build();
	}

	/** Bounded both ways: a person's first request waits on this call. */
	private static JdkClientHttpRequestFactory requestFactory() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
		factory.setReadTimeout(Duration.ofSeconds(5));
		return factory;
	}

	@Override
	public Optional<Identity> find(String userId) {
		try {
			Envelope envelope = this.client.get()
				.uri("/api/applications/{app}/users/{uid}", this.applicationId, userId)
				.retrieve()
				.onStatus((status) -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
				})
				.body(Envelope.class);
			if (envelope == null || envelope.data() == null) {
				return Optional.empty();
			}
			Member member = envelope.data();
			if (member.deletedAt() != null || member.email() == null) {
				return Optional.empty();
			}
			return Optional.of(new Identity(member.userId(), member.email(), member.displayName()));
		}
		catch (RestClientException ex) {
			throw new IdentityUnavailableException("elvix did not answer the member lookup", ex);
		}
	}

	/** elvix's management envelope; only the fields read here. */
	record Envelope(boolean success, Member data) {
	}

	record Member(String userId, String email, String displayName, String deletedAt) {
	}

}
