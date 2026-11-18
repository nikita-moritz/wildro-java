package app.wildro.event.moderation.notice;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Resend's {@code POST /emails}, the transport nikone.dev already sends with. */
@Component
class ResendMailer implements Mailer {

	private final RestClient client;

	private final String apiKey;

	private final String from;

	private final String replyTo;

	ResendMailer(RestClient.Builder builder, @Value("${wildro.mail.resend-api-key:}") String apiKey,
			@Value("${wildro.mail.from}") String from, @Value("${wildro.mail.reply-to}") String replyTo) {
		this.apiKey = apiKey;
		this.from = from;
		this.replyTo = replyTo;
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(Duration.ofSeconds(15));
		this.client = builder.baseUrl("https://api.resend.com").requestFactory(factory).build();
	}

	@Override
	public boolean enabled() {
		return !this.apiKey.isBlank();
	}

	@Override
	public void send(String to, String subject, String text) {
		try {
			this.client.post()
				.uri("/emails")
				.header("Authorization", "Bearer " + this.apiKey)
				.body(Map.of("from", this.from, "to", List.of(to), "reply_to", this.replyTo, "subject", subject, "text",
						text))
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientException ex) {
			throw new MailFailedException("Resend refused or did not answer", ex);
		}
	}

}
