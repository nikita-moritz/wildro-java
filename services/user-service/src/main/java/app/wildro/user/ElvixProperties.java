package app.wildro.user;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How this service reaches elvix and recognises what elvix sends it.
 *
 * @param baseUrl the elvix origin.
 * @param applicationId the application's internal id, which the management API
 *        is addressed by. Not the clientId: that one is the token audience.
 * @param apiKey an {@code eak_} key of this application, server side only. Read
 *        access is enough: the service only looks people up.
 * @param webhookSecret the secret elvix signs deliveries with. Blank refuses
 *        every delivery.
 * @param webhookTolerance how far a delivery's signed time may lie from ours
 *        before it is treated as a replay.
 */
@ConfigurationProperties("elvix")
public record ElvixProperties(String baseUrl, String applicationId, String apiKey, String webhookSecret,
		Duration webhookTolerance) {

	public boolean webhooksEnabled() {
		return this.webhookSecret != null && !this.webhookSecret.isBlank();
	}

}
