package app.wildro.event.own;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.wildro.event.domain.InvalidEventException;

/**
 * {@code /v1/own/events}: a signed-in person's own events. Reached only through
 * the frontend's server, with the person's elvix JWT. Refusals are problem
 * details whose {@code code} the site turns into a sentence.
 */
@RestController
@RequestMapping("/v1/own/events")
class OwnEventController {

	private final OwnEventService service;

	OwnEventController(OwnEventService service) {
		this.service = service;
	}

	@GetMapping
	List<OwnEventView> mine(@AuthenticationPrincipal Jwt token) {
		return this.service.mine(token);
	}

	@GetMapping("/{slug}")
	OwnEventView one(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable String slug) {
		return this.service.one(token, authentication, slug);
	}

	@PostMapping
	ResponseEntity<OwnEventView> create(@AuthenticationPrincipal Jwt token,
			@RequestParam(defaultValue = "false") boolean publish, @Valid @RequestBody OwnEventRequest request) {
		OwnEventView created = this.service.create(token, request, publish);
		return ResponseEntity.created(URI.create("/v1/own/events/" + created.slug())).body(created);
	}

	@PutMapping("/{slug}")
	OwnEventView update(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable String slug,
			@Valid @RequestBody OwnEventRequest request) {
		return this.service.update(token, authentication, slug, request);
	}

	@PostMapping("/{slug}/publish")
	OwnEventView publish(@AuthenticationPrincipal Jwt token, @PathVariable String slug) {
		return this.service.publish(token, slug);
	}

	@PostMapping("/{slug}/cancel")
	OwnEventView cancel(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable String slug) {
		return this.service.cancel(token, authentication, slug);
	}

	@ExceptionHandler(OwnEventRefusal.class)
	ProblemDetail refused(OwnEventRefusal refusal) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(refusal.code().status(), refusal.getMessage());
		problem.setType(URI.create("https://java.wildro.app/problems/" + refusal.code().name().toLowerCase()));
		problem.setProperty("code", refusal.code().name());
		return problem;
	}

	@ExceptionHandler(InvalidEventException.class)
	ProblemDetail invalid(InvalidEventException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getReason());
		problem.setType(URI.create("https://java.wildro.app/problems/invalid-event"));
		problem.setProperty("code", "INVALID");
		problem.setProperty("field", ex.getField());
		return problem;
	}

	@ExceptionHandler(WriterUnavailableException.class)
	ProblemDetail unavailable() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The account service did not answer. Nothing was written.");
		problem.setProperty("code", "UNAVAILABLE");
		return problem;
	}

}
