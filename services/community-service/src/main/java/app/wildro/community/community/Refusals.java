package app.wildro.community.community;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import app.wildro.community.people.PeopleUnavailableException;

/**
 * Refusals as problem details with a stable {@code code}, for every controller
 * of the service: the members' API and moderation alike. Bound to one
 * controller, a moderation refusal surfaced as a 500.
 */
@RestControllerAdvice
class Refusals {

	@ExceptionHandler(CommunityRefusal.class)
	ProblemDetail refused(CommunityRefusal refusal) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(refusal.code().status(), refusal.getMessage());
		problem.setType(URI.create("https://java.wildro.app/problems/" + refusal.code().name().toLowerCase()));
		problem.setProperty("code", refusal.code().name());
		return problem;
	}

	@ExceptionHandler(PeopleUnavailableException.class)
	ProblemDetail unavailable() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The account service did not answer. Nothing was written.");
		problem.setProperty("code", "UNAVAILABLE");
		return problem;
	}

}
