package app.wildro.user.webhook;

import java.time.Instant;

/**
 * The envelope elvix sends, with the parts of {@code data} this service reads.
 * Unknown fields are ignored, and so are unknown types: elvix adds event types
 * over time and an older receiver must keep working.
 */
record WebhookEvent(String id, String type, Instant createdAt, String applicationId, Data data) {

	record Data(String userId) {
	}

}
