package app.wildro.analytics.track;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Garbage reaching the track endpoint is answered like any other view: accepted
 * and dropped. Without this, a broken body would surface as a 400 problem detail:
 * Boot's own problem-details handler catches the same exceptions, so this one
 * has to come first.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TrackController.class)
class MalformedBodyAdvice {

	@ExceptionHandler({ HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class })
	ResponseEntity<Void> dropped() {
		return ResponseEntity.accepted().build();
	}

}
