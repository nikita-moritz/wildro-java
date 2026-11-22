package app.wildro.community.moderation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import app.wildro.community.community.CommunityRefusal;
import app.wildro.community.community.CommunityService;

/**
 * {@code POST /v1/communities/{slug}/reports}: a report on a community, open to
 * guests (DSA Art. 16). The frontend passes the visitor's address for the rate
 * limit only; it is never stored, and no account is asked for.
 */
@RestController
class ReportController {

	private final CommunityService service;

	private final ReportRateLimiter limiter;

	ReportController(CommunityService service, ReportRateLimiter limiter) {
		this.service = service;
		this.limiter = limiter;
	}

	@PostMapping("/v1/communities/{slug}/reports")
	ResponseEntity<Void> report(@RequestHeader(name = "X-Client-Address", required = false) String address,
			@PathVariable String slug, @Valid @RequestBody ReportRequest request) {
		if (!this.limiter.tryAcquire(address)) {
			throw new CommunityRefusal(CommunityRefusal.Code.TOO_MANY, "too many reports from this address");
		}
		this.service.report(slug, request.reason().name(), request.details());
		return ResponseEntity.accepted().build();
	}

	record ReportRequest(@NotNull ReportReason reason, @NotBlank @Size(max = 2000) String details) {
	}

}
