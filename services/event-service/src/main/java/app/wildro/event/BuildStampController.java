package app.wildro.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Root page of the deployment. It exists so that a deploy can be confirmed by
 * eye, at the real hostname, and so that "the browser is showing an older build"
 * is answerable instead of arguable: the revision below is the commit the running
 * jar was built from.
 * <p>
 * It is a placeholder, not product. It is replaced by the public event list the
 * moment stage one has one, and it carries no copy that would need translating
 * into de/en/ru in the meantime.
 */
@RestController
class BuildStampController {

	private final String environment;

	private final String revision;

	BuildStampController(
			@Value("${APP_ENVIRONMENT:local}") String environment,
			@Value("${APP_REVISION:unknown}") String revision) {
		this.environment = environment;
		this.revision = revision;
	}

	@GetMapping(path = "/", produces = MediaType.TEXT_PLAIN_VALUE)
	String stamp() {
		return """
				event-service
				environment: %s
				revision:    %s
				""".formatted(this.environment, this.revision);
	}

}
