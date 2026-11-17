package app.wildro.analytics.track;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /v1/track. Answers 202 to anything, including a body that is not JSON at
 * all: the caller is a beacon that discards the answer, so an error status would
 * help nobody (SPEC.md §13).
 */
@RestController
class TrackController {

	private final PageViewRecorder recorder;

	TrackController(PageViewRecorder recorder) {
		this.recorder = recorder;
	}

	@PostMapping("/v1/track")
	ResponseEntity<Void> track(@RequestBody(required = false) TrackRequest view) {
		this.recorder.record(view);
		return ResponseEntity.accepted().build();
	}

}
