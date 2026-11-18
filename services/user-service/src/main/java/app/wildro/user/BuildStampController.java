package app.wildro.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Root page of the deployment, the same contract as the event service: the
 * deploy script reads the revision line to confirm the restarted process is the
 * one it just built.
 */
@RestController
class BuildStampController {

	private final String environment;

	private final String revision;

	BuildStampController(@Value("${APP_ENVIRONMENT:local}") String environment,
			@Value("${APP_REVISION:unknown}") String revision) {
		this.environment = environment;
		this.revision = revision;
	}

	@GetMapping(path = "/", produces = MediaType.TEXT_PLAIN_VALUE)
	String stamp() {
		return """
				user-service
				environment: %s
				revision:    %s
				""".formatted(this.environment, this.revision);
	}

}
