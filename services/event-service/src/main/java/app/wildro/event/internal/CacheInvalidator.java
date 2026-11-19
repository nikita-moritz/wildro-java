package app.wildro.event.internal;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Tells the frontend which cached renders a change made stale, through its
 * {@code POST /api/revalidate} (one contract, the tags the frontend accepts).
 * Called after the change committed, never inside it: a frontend that does not
 * answer must not roll a decision back. Best effort by design, since every
 * cached render also expires on its own within hours.
 */
@Component
public class CacheInvalidator {

	private static final Logger log = LoggerFactory.getLogger(CacheInvalidator.class);

	private final RestClient client;

	private final boolean configured;

	CacheInvalidator(RestClient.Builder builder, @Value("${wildro.frontend.revalidate-url:}") String url,
			@Value("${wildro.frontend.revalidate-secret:}") String secret) {
		this.configured = !url.isBlank() && secret.length() >= 32;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.client = builder.baseUrl(this.configured ? url : "http://127.0.0.1:1")
			.defaultHeader("Authorization", "Bearer " + secret)
			.requestFactory(factory)
			.build();
	}

	public void invalidate(Collection<String> tags) {
		if (!this.configured || tags.isEmpty()) {
			return;
		}
		try {
			this.client.post().body(Map.of("tags", tags.stream().distinct().limit(50).toList())).retrieve().toBodilessEntity();
		}
		catch (RestClientException ex) {
			log.warn("frontend did not take the revalidation of {}: {}", tags, ex.getMessage());
		}
	}

}
