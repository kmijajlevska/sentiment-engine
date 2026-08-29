package mk.ukim.finki.sentimentengine.api;

import mk.ukim.finki.sentimentengine.data.dto.BucketDetailsDTO;
import mk.ukim.finki.sentimentengine.data.dto.BucketMetricsDTO;
import mk.ukim.finki.sentimentengine.data.entity.TimeResolution;
import mk.ukim.finki.sentimentengine.service.AggregationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link EventAnalyticsController} using the MVC slice.
 *
 * <p>Maps to user scenario 4.3 (Visualization & Analytics): the timeseries endpoint returns
 * per-bucket metrics for a chosen event type / range / resolution, and the details endpoint returns
 * the individual events aggregated into a chosen bucket. Invalid ranges (from after to) are rejected.
 */
@WebMvcTest(EventAnalyticsController.class)
class EventAnalyticsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AggregationService aggregationService;

	@Test
	@DisplayName("GET /analytics/timeseries returns bucket metrics")
	void timeSeriesReturnsMetrics() throws Exception {
		when(aggregationService.getMetricsPerTimeBucket(eq("PushEvent"), anyLong(), anyLong(), eq(TimeResolution.HOUR)))
			.thenReturn(List.of(new BucketMetricsDTO(0L, 5L, 0.3, -0.1, 0.9, 1L)));

		mockMvc.perform(get("/analytics/timeseries")
			       .param("eventType", "PushEvent")
			       .param("from", "2024-01-01T00:00:00Z")
			       .param("to", "2024-01-02T00:00:00Z")
			       .param("resolution", "HOUR"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$[0].eventCount").value(5))
		       .andExpect(jsonPath("$[0].avgSentiment").value(0.3));
	}

	@Test
	@DisplayName("GET /analytics/timeseries rejects a range where 'from' is after 'to'")
	void timeSeriesRejectsInvalidRange() {
		// The controller guards against an inverted range by throwing IllegalArgumentException.
		// There is no global exception handler, so MockMvc surfaces it as a resolved exception.
		org.assertj.core.api.Assertions.assertThatThrownBy(() ->
			mockMvc.perform(get("/analytics/timeseries")
				       .param("from", "2024-01-02T00:00:00Z")
				       .param("to", "2024-01-01T00:00:00Z")
				       .param("resolution", "HOUR")))
			.hasRootCauseInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("from must not be after to");

		verifyNoInteractions(aggregationService);
	}

	@Test
	@DisplayName("GET /analytics/details returns individual events plus a summary for a bucket")
	void detailsReturnsBucketDrillDown() throws Exception {
		BucketMetricsDTO summary = new BucketMetricsDTO(60000L, 0L, 0.0, 0.0, 0.0, 0L);
		when(aggregationService.getDetailSummary(isNull(), anyLong(), eq(TimeResolution.MINUTE)))
			.thenReturn(new BucketDetailsDTO(Collections.emptyList(), summary));

		mockMvc.perform(get("/analytics/details")
			       .param("bucket", "1970-01-01T00:01:00Z")
			       .param("resolution", "MINUTE"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.summary.bucketStart").value(60000))
		       .andExpect(jsonPath("$.events").isArray());
	}
}
