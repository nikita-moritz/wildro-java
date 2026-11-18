package app.wildro.user.webhook;

import java.time.Clock;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import app.wildro.user.ElvixProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /v1/webhooks/elvix}, reached through the frontend's
 * {@code /api/webhooks/elvix}, which forwards the body byte for byte. The body
 * is read as a string because the signature covers the exact bytes elvix sent;
 * parsing first and serialising again would sign something else.
 *
 * Answers: 204 applied or already applied, 401 unsigned or stale, 400 not an
 * event of this application. elvix retries anything else, so a failure while
 * applying surfaces as a 5xx on purpose.
 */
@RestController
class ElvixWebhookController {

	private final ElvixProperties elvix;

	private final LifecycleHandler handler;

	private final JsonMapper json;

	private final Clock clock;

	ElvixWebhookController(ElvixProperties elvix, LifecycleHandler handler, JsonMapper json, Clock clock) {
		this.elvix = elvix;
		this.handler = handler;
		this.json = json;
		this.clock = clock;
	}

	@PostMapping("/v1/webhooks/elvix")
	ResponseEntity<Void> receive(@RequestHeader(name = "Elvix-Signature", required = false) String signature,
			@RequestBody String body) {
		if (!this.elvix.webhooksEnabled() || !WebhookSignature.valid(signature, body, this.elvix.webhookSecret(),
				this.clock, this.elvix.webhookTolerance())) {
			return ResponseEntity.status(401).build();
		}
		WebhookEvent event;
		try {
			event = this.json.readValue(body, WebhookEvent.class);
		}
		catch (JacksonException ex) {
			return ResponseEntity.badRequest().build();
		}
		if (event.id() == null || event.type() == null || event.createdAt() == null
				|| !this.elvix.applicationId().equals(event.applicationId())) {
			return ResponseEntity.badRequest().build();
		}
		this.handler.apply(event);
		return ResponseEntity.noContent().build();
	}

}
