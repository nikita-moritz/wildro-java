package app.wildro.community.moderation;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.wildro.community.community.CommunityService;
import app.wildro.community.community.Views;

/**
 * What the platform may do to a community (SPEC.md §18.1, §18.4): take it off
 * the site and put it back (MODERATOR or SUPERUSER), or hand its ownership to
 * one of its admins (SUPERUSER), by a moderation decision, with an audit entry.
 * It never touches roles inside a community otherwise. The roles are checked in
 * the security configuration.
 */
@RestController
class ModerationController {

	private final CommunityService service;

	ModerationController(CommunityService service) {
		this.service = service;
	}

	@GetMapping("/v1/moderation/communities")
	List<Views.Moderated> list(Authentication authentication, @RequestParam(defaultValue = "") String q) {
		boolean superuser = authentication.getAuthorities()
			.stream()
			.anyMatch((authority) -> "ROLE_superuser".equals(authority.getAuthority()));
		return this.service.moderated(q, superuser);
	}

	@PostMapping("/v1/moderation/communities/{slug}/suspend")
	ResponseEntity<Void> suspend(@AuthenticationPrincipal Jwt token, @PathVariable String slug,
			@Valid @RequestBody Reason request) {
		this.service.suspend(token.getSubject(), slug, request.reason());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/v1/moderation/communities/{slug}/reinstate")
	ResponseEntity<Void> reinstate(@AuthenticationPrincipal Jwt token, @PathVariable String slug) {
		this.service.reinstate(token.getSubject(), slug);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/v1/moderation/communities/{slug}/transfer")
	ResponseEntity<Void> transfer(@AuthenticationPrincipal Jwt token, @PathVariable String slug,
			@Valid @RequestBody Transfer request) {
		this.service.transferByModeration(token.getSubject(), slug, request.userId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/v1/moderation/reports/{id}/dismiss")
	ResponseEntity<Void> dismiss(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
		this.service.dismissReport(token.getSubject(), id);
		return ResponseEntity.noContent().build();
	}

	record Reason(@NotBlank @Size(max = 2000) String reason) {
	}

	record Transfer(@NotBlank String userId) {
	}

}
