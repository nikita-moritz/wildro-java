package app.wildro.event.moderation.notice;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * elvix's member lookup, the same call the user service makes on a first visit.
 * Asked here directly rather than through the user service so that no
 * service-to-service credential has to exist; the address is the one the person
 * keeps current in elvix, and a deleted account answers with nothing.
 */
@Component
class ElvixContactDirectory implements ContactDirectory {

	private final RestClient client;

	private final String applicationId;

	ElvixContactDirectory(RestClient.Builder builder, @Value("${elvix.base-url}") String baseUrl,
			@Value("${elvix.application-id:}") String applicationId, @Value("${elvix.api-key:}") String apiKey) {
		this.applicationId = applicationId;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.client = builder.baseUrl(baseUrl)
			.defaultHeader("Authorization", "Bearer " + apiKey)
			.requestFactory(factory)
			.build();
	}

	@Override
	public Optional<String> emailOf(String userId) {
		try {
			Envelope envelope = this.client.get()
				.uri("/api/applications/{app}/users/{uid}", this.applicationId, userId)
				.retrieve()
				.onStatus((status) -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
				})
				.body(Envelope.class);
			if (envelope == null || envelope.data() == null || envelope.data().deletedAt() != null) {
				return Optional.empty();
			}
			return Optional.ofNullable(envelope.data().email());
		}
		catch (RestClientException ex) {
			throw new MailFailedException("elvix did not answer the member lookup", ex);
		}
	}

	record Envelope(Member data) {
	}

	record Member(String email, String deletedAt) {
	}

}
