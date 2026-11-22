package app.wildro.event.moderation;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports in, decisions out. {@code POST /v1/reports} is open to guests; the
 * frontend passes the visitor's address in {@code X-Client-Address} for the rate
 * limit and nothing else. {@code /v1/moderation/**} needs the moderator or
 * superuser role from elvix, checked in the security configuration.
 */
@RestController
class ModerationController {

	private final ModerationService moderation;

	private final ReportRateLimiter limiter;

	ModerationController(ModerationService moderation, ReportRateLimiter limiter) {
		this.moderation = moderation;
		this.limiter = limiter;
	}

	@PostMapping("/v1/reports")
	ResponseEntity<Void> report(@RequestHeader(name = "X-Client-Address", required = false) String address,
			@AuthenticationPrincipal Jwt token, @Valid @RequestBody ReportRequest request) {
		if (!this.limiter.tryAcquire(address)) {
			throw new ModerationRefusal(ModerationRefusal.Code.TOO_MANY, "too many reports from this address");
		}
		this.moderation.report(request.slug(), request.reason(), request.details(),
				token == null ? null : token.getSubject());
		return ResponseEntity.accepted().build();
	}

	@GetMapping("/v1/moderation/reports")
	List<ModerationService.OpenReport> open() {
		return this.moderation.openReports();
	}

	@PostMapping("/v1/moderation/events/{slug}/suspend")
	ResponseEntity<Void> suspend(@AuthenticationPrincipal Jwt token, @PathVariable String slug,
			@Valid @RequestBody SuspendRequest request) {
		this.moderation.suspend(slug, request.reason(), token.getSubject());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/v1/moderation/events/{slug}/reinstate")
	ResponseEntity<Void> reinstate(@AuthenticationPrincipal Jwt token, @PathVariable String slug) {
		this.moderation.reinstate(slug, token.getSubject());
		return ResponseEntity.noContent().build();
	}

	/** The organiser's own picture alone comes down; the event stays and its owner is told why. */
	@PostMapping("/v1/moderation/events/{slug}/cover/remove")
	ResponseEntity<Void> removeCover(@AuthenticationPrincipal Jwt token, @PathVariable String slug,
			@Valid @RequestBody SuspendRequest request) {
		this.moderation.removeCover(slug, request.reason(), token.getSubject());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/v1/moderation/reports/{id}/dismiss")
	ResponseEntity<Void> dismiss(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
		this.moderation.dismiss(id, token.getSubject());
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(ModerationRefusal.class)
	ProblemDetail refused(ModerationRefusal refusal) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(refusal.code().status(), refusal.getMessage());
		problem.setType(URI.create("https://java.wildro.app/problems/" + refusal.code().name().toLowerCase()));
		problem.setProperty("code", refusal.code().name());
		return problem;
	}

	record ReportRequest(@NotBlank @Size(max = 200) String slug, @NotNull ReportReason reason,
			@NotBlank @Size(max = 2000) String details) {
	}

	record SuspendRequest(@NotBlank @Size(max = 2000) String reason) {
	}

}
