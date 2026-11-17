package app.wildro.analytics.stats;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /v1/stats?period=day|week|month|all. Internal: the service listens on
 * loopback, and the frontend's /api/stats is the public door, checking the
 * dashboard's key before it asks here.
 */
@RestController
class StatsController {

	private final StatsReader reader;

	StatsController(StatsReader reader) {
		this.reader = reader;
	}

	@GetMapping("/v1/stats")
	ResponseEntity<ProjectStats> stats(@RequestParam(required = false) String period) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(this.reader.read(Period.parse(period)));
	}

}
