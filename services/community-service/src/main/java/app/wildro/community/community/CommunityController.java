package app.wildro.community.community;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /v1/communities}: the list and the page are public (they are search
 * traffic, §18.3); everything else needs the person's elvix JWT and is decided
 * by {@link CommunityService}. Refusals are problem details with a {@code code}
 * ({@link Refusals}).
 */
@RestController
class CommunityController {

	static final int PAGE_SIZE = 24;

	private final CommunityService service;

	CommunityController(CommunityService service) {
		this.service = service;
	}

	@GetMapping("/v1/communities")
	Map<String, Object> list(@RequestParam(defaultValue = "0") int page) {
		int safe = Math.max(0, Math.min(page, 50));
		List<Views.Summary> rows = this.service.list(safe, PAGE_SIZE);
		return Map.of("items", rows.stream().limit(PAGE_SIZE).toList(), "page", safe, "hasNext", rows.size() > PAGE_SIZE);
	}

	@GetMapping("/v1/communities/{slug}")
	Views.Page page(@PathVariable String slug) {
		return this.service.page(slug);
	}

	@GetMapping("/v1/communities/{slug}/me")
	Views.Standing standing(@AuthenticationPrincipal Jwt token, @PathVariable String slug) {
		return this.service.standing(token, slug);
	}

	@GetMapping("/v1/communities/{slug}/members")
	List<Views.Member> members(@AuthenticationPrincipal Jwt token, @PathVariable String slug) {
		return this.service.members(token, slug);
	}

	@GetMapping("/v1/me/communities")
	List<Views.Standing> mine(@AuthenticationPrincipal Jwt token) {
		return this.service.mine(token);
	}

	@PostMapping("/v1/communities")
	ResponseEntity<Views.Page> create(@AuthenticationPrincipal Jwt token, @Valid @RequestBody CommunityRequest request) {
		Views.Page created = this.service.create(token, request);
		return ResponseEntity.created(URI.create("/v1/communities/" + created.slug())).body(created);
	}

	@PutMapping("/v1/communities/{slug}")
	Views.Page update(@AuthenticationPrincipal Jwt token, @PathVariable String slug,
			@Valid @RequestBody CommunityRequest request) {
		return this.service.update(token, slug, request);
	}

	@PostMapping("/v1/communities/{slug}/join")
	Views.Standing join(@AuthenticationPrincipal Jwt token, @PathVariable String slug) {
		return this.service.join(token, slug);
	}

	@PostMapping("/v1/communities/{slug}/leave")
	Views.Standing leave(@AuthenticationPrincipal Jwt token, @PathVariable String slug) {
		return this.service.leave(token, slug);
	}

	@PostMapping("/v1/communities/{slug}/members/{userId}/role")
	ResponseEntity<Void> role(@AuthenticationPrincipal Jwt token, @PathVariable String slug, @PathVariable String userId,
			@Valid @RequestBody RoleRequest request) {
		this.service.setRole(token, slug, userId, request.role());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/v1/communities/{slug}/members/{userId}/remove")
	ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt token, @PathVariable String slug, @PathVariable String userId) {
		this.service.remove(token, slug, userId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/v1/communities/{slug}/transfer")
	ResponseEntity<Void> transfer(@AuthenticationPrincipal Jwt token, @PathVariable String slug,
			@Valid @RequestBody TransferRequest request) {
		this.service.transfer(token, slug, request.userId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/v1/communities/{slug}/close")
	ResponseEntity<Void> close(@AuthenticationPrincipal Jwt token, @PathVariable String slug) {
		this.service.close(token, slug);
		return ResponseEntity.noContent().build();
	}

	record RoleRequest(@NotNull Role role) {
	}

	record TransferRequest(@NotBlank String userId) {
	}

}
