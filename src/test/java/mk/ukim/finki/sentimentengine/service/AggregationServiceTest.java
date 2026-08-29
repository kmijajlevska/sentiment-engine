package mk.ukim.finki.sentimentengine.service;

import mk.ukim.finki.sentimentengine.data.dto.BucketDetailsDTO;
import mk.ukim.finki.sentimentengine.data.dto.BucketMetricsDTO;
import mk.ukim.finki.sentimentengine.data.entity.EvaluationStatus;
import mk.ukim.finki.sentimentengine.data.entity.ProcessedEvent;
import mk.ukim.finki.sentimentengine.data.entity.TimeResolution;
import mk.ukim.finki.sentimentengine.data.service.ProcessedEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AggregationService}.
 *
 * <p>Maps to user scenario 4.3 (Visualization & Analytics): the analytics layer returns aggregated
 * metrics per time bucket at five resolutions and a detailed drill-down of the individual events in
 * a chosen bucket. The service must route to the correct query per resolution and compute summary
 * statistics for the detail view.
 */
@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

	@Mock
	private ProcessedEventService processedEventService;

	private AggregationService service;

	@BeforeEach
	void setUp() {
		service = new AggregationService(processedEventService);
	}

	// row layout expected by AggregationUtils.mapRowToBucketMetrics:
	// [bucketStart, eventCount, avgSentiment, minSentiment, maxSentiment, absenceCount]
	private Object[] row(long bucketStart, long count, double avg, double min, double max, long absence) {
		return new Object[]{bucketStart, count, avg, min, max, absence};
	}

	@Test
	@DisplayName("MINUTE resolution routes to the minute aggregation query and maps rows")
	void minuteResolutionMapsRows() {
		List<Object[]> rows = java.util.Collections.singletonList(row(0L, 3L, 0.5, -0.2, 0.9, 1L));
		when(processedEventService.aggregateByMinute("PushEvent", 0L, 60000L)).thenReturn(rows);

		List<BucketMetricsDTO> result = service.getMetricsPerTimeBucket("PushEvent", 0L, 60000L, TimeResolution.MINUTE);

		assertThat(result).hasSize(1);
		BucketMetricsDTO dto = result.get(0);
		assertThat(dto.bucketStart()).isEqualTo(0L);
		assertThat(dto.eventCount()).isEqualTo(3L);
		assertThat(dto.avgSentiment()).isEqualTo(0.5);
		assertThat(dto.minSentiment()).isEqualTo(-0.2);
		assertThat(dto.maxSentiment()).isEqualTo(0.9);
		assertThat(dto.absenceCount()).isEqualTo(1L);
	}

	@Test
	@DisplayName("HOUR resolution routes to the hour aggregation query")
	void hourResolutionRoutesCorrectly() {
		when(processedEventService.aggregateByHour(any(), anyLong(), anyLong())).thenReturn(List.of());

		service.getMetricsPerTimeBucket("PushEvent", 0L, 3600000L, TimeResolution.HOUR);

		verify(processedEventService).aggregateByHour("PushEvent", 0L, 3600000L);
	}

	@Test
	@DisplayName("DAY resolution routes to the day aggregation query with truncated dates")
	void dayResolutionRoutesCorrectly() {
		when(processedEventService.aggregateByDay(any(), any(), any())).thenReturn(List.of());

		service.getMetricsPerTimeBucket("PushEvent", 0L, 86400000L, TimeResolution.DAY);

		verify(processedEventService).aggregateByDay(eq("PushEvent"), any(), any());
	}

	@Test
	@DisplayName("WEEK and MONTH resolutions route to their respective queries")
	void weekAndMonthResolutionsRouteCorrectly() {
		when(processedEventService.aggregateByWeek(any(), any(), any())).thenReturn(List.of());
		when(processedEventService.aggregateByMonth(any(), any(), any())).thenReturn(List.of());

		service.getMetricsPerTimeBucket("PushEvent", 0L, 1L, TimeResolution.WEEK);
		service.getMetricsPerTimeBucket("PushEvent", 0L, 1L, TimeResolution.MONTH);

		verify(processedEventService).aggregateByWeek(eq("PushEvent"), any(), any());
		verify(processedEventService).aggregateByMonth(eq("PushEvent"), any(), any());
	}

	@Test
	@DisplayName("Detail view returns an empty summary when the bucket has no events")
	void detailReturnsEmptySummaryWhenNoEvents() {
		when(processedEventService.findByEventTypeAndMinuteBucket("PushEvent", 60000L)).thenReturn(List.of());

		BucketDetailsDTO details = service.getDetailSummary("PushEvent", 60000L, TimeResolution.MINUTE);

		assertThat(details.events()).isEmpty();
		assertThat(details.summary().eventCount()).isEqualTo(0L);
		assertThat(details.summary().bucketStart()).isEqualTo(60000L);
	}

	@Test
	@DisplayName("Detail view returns individual events and a computed summary")
	void detailReturnsEventsAndComputedSummary() {
		ProcessedEvent e1 = processed(1L, "PushEvent", 0.4);
		ProcessedEvent e2 = processed(2L, "PushEvent", 0.8);
		when(processedEventService.findByEventTypeAndMinuteBucket("PushEvent", 60000L))
			.thenReturn(List.of(e1, e2));

		BucketDetailsDTO details = service.getDetailSummary("PushEvent", 60000L, TimeResolution.MINUTE);

		assertThat(details.events()).hasSize(2);
		assertThat(details.summary().eventCount()).isEqualTo(2L);
		assertThat(details.summary().avgSentiment()).isCloseTo(0.6, org.assertj.core.api.Assertions.within(1e-9));
		assertThat(details.summary().minSentiment()).isEqualTo(0.4);
		assertThat(details.summary().maxSentiment()).isEqualTo(0.8);
	}

	private ProcessedEvent processed(long eventId, String type, double score) {
		return ProcessedEvent.builder()
		                     .eventId(eventId)
		                     .eventType(type)
		                     .eventTimestamp(60000L)
		                     .sentimentScore(BigDecimal.valueOf(score))
		                     .confidence(BigDecimal.valueOf(1.0))
		                     .appliedRuleId(10L)
		                     .minuteBucket(60000L)
		                     .hourBucket(0L)
		                     .evaluationStatus(EvaluationStatus.COMPLETED)
		                     .build();
	}
}
