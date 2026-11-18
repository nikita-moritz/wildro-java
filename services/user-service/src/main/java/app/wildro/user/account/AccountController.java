package app.wildro.user.account;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import app.wildro.user.identity.IdentityUnavailableException;

/**
 * The signed-in person's own account. There is no route that takes a user id:
 * the subject of the token is the only person these endpoints can reach.
 */
@RestController
class AccountController {

	private final AccountService accounts;

	AccountController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping("/v1/me")
	Me me(@AuthenticationPrincipal Jwt jwt, JwtAuthenticationToken authentication) {
		return Me.of(this.accounts.current(jwt.getSubject()), roles(authentication), this.accounts.terms());
	}

	@PutMapping("/v1/me/display-name")
	Me rename(@AuthenticationPrincipal Jwt jwt, JwtAuthenticationToken authentication,
			@RequestBody DisplayNameRequest request) {
		return Me.of(this.accounts.rename(jwt.getSubject(), request.displayName()), roles(authentication),
				this.accounts.terms());
	}

	@PostMapping("/v1/me/terms")
	Me acceptTerms(@AuthenticationPrincipal Jwt jwt, JwtAuthenticationToken authentication,
			@RequestBody TermsRequest request) {
		return Me.of(this.accounts.acceptTerms(jwt.getSubject(), request.version()), roles(authentication),
				this.accounts.terms());
	}

	/** Role slugs as elvix put them in the token: {@code user}, {@code moderator}, {@code superuser}. */
	private static List<String> roles(JwtAuthenticationToken authentication) {
		return authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter((authority) -> authority.startsWith("ROLE_"))
			.map((authority) -> authority.substring("ROLE_".length()))
			.sorted()
			.toList();
	}

	@ExceptionHandler(AccountService.InvalidDisplayNameException.class)
	ProblemDetail invalidName() {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-display-name",
				"A display name is 2 to 50 characters, without control characters.");
	}

	@ExceptionHandler(AccountService.TermsNotPublishedException.class)
	ProblemDetail termsNotPublished() {
		return problem(HttpStatus.CONFLICT, "terms-not-published", "No terms are in force yet.");
	}

	@ExceptionHandler(AccountService.StaleTermsException.class)
	ProblemDetail staleTerms(AccountService.StaleTermsException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "stale-terms",
				"The terms changed since they were shown. Show the current version and ask again.");
		problem.setProperty("currentVersion", ex.current());
		return problem;
	}

	@ExceptionHandler(AccountService.UnknownPersonException.class)
	ProblemDetail unknownPerson() {
		return problem(HttpStatus.UNAUTHORIZED, "unknown-person", "The identity provider no longer knows this account.");
	}

	@ExceptionHandler(IdentityUnavailableException.class)
	ProblemDetail identityUnavailable() {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "identity-unavailable",
				"The identity provider did not answer. Try again shortly.");
	}

	private static ProblemDetail problem(HttpStatus status, String type, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("https://java.wildro.app/problems/" + type));
		return problem;
	}

	record DisplayNameRequest(String displayName) {
	}

	record TermsRequest(String version) {
	}

	/**
	 * The account as its owner sees it. The address is shown to the owner only;
	 * no other route of any service hands it out.
	 */
	record Me(String id, String email, String displayName, List<String> roles, Terms terms) {

		static Me of(Account account, List<String> roles, TermsProperties terms) {
			boolean upToDate = terms.published() && terms.version().equals(account.termsVersion());
			return new Me(account.id(), account.email(), account.displayName(), roles,
					new Terms(terms.published() ? terms.version() : null, account.termsVersion(),
							account.termsAcceptedAt(), upToDate));
		}

	}

	record Terms(String current, String accepted, Instant acceptedAt, boolean upToDate) {
	}

}
