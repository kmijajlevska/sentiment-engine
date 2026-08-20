package mk.ukim.finki.sentimentengine.api;

import lombok.RequiredArgsConstructor;
import mk.ukim.finki.sentimentengine.data.dto.BucketDetailsDTO;
import mk.ukim.finki.sentimentengine.data.dto.BucketMetricsDTO;
import mk.ukim.finki.sentimentengine.data.entity.TimeResolution;
import mk.ukim.finki.sentimentengine.service.AggregationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * @author kristina
 */
@RestController
@RequestMapping("analytics")
@RequiredArgsConstructor
public class EventAnalyticsController {

	private final AggregationService aggregationService;


	@GetMapping("/timeseries")
	public ResponseEntity<List<BucketMetricsDTO>> getTimeSeries(@RequestParam(required = false) String eventType,
	                                                            @RequestParam Instant from,
	                                                            @RequestParam Instant to,
	                                                            @RequestParam TimeResolution resolution) {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("from must not be after to");
		}
		List<BucketMetricsDTO> result = aggregationService.getMetricsPerTimeBucket(eventType, from.toEpochMilli(), to.toEpochMilli(), resolution);
		return ResponseEntity.ok(result);
	}

	@GetMapping("/details")
	public ResponseEntity<BucketDetailsDTO> getDetails(@RequestParam(required = false) String eventType,
	                                                   @RequestParam Instant bucket,
	                                                   @RequestParam TimeResolution resolution) {
		BucketDetailsDTO response = aggregationService.getDetailSummary(eventType, bucket.toEpochMilli(), resolution);
		return ResponseEntity.ok(response);
	}
}
