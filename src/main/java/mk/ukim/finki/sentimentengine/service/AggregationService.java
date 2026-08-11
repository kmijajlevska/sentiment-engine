package mk.ukim.finki.sentimentengine.service;


import mk.ukim.finki.sentimentengine.data.dto.BucketDetailsDTO;
import mk.ukim.finki.sentimentengine.data.dto.BucketMetricsDTO;
import mk.ukim.finki.sentimentengine.data.dto.ProcessedEventDTO;
import mk.ukim.finki.sentimentengine.data.entity.ProcessedEvent;
import mk.ukim.finki.sentimentengine.data.entity.TimeResolution;
import mk.ukim.finki.sentimentengine.data.service.ProcessedEventService;
import mk.ukim.finki.sentimentengine.util.AggregationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author kristina
 */
@Service
public class AggregationService {

	private static final Logger logger = LoggerFactory.getLogger(AggregationService.class);
	private final ProcessedEventService processedEventService;

	public AggregationService(ProcessedEventService processedEventService) {
		this.processedEventService = processedEventService;
	}

	public List<BucketMetricsDTO> getMetricsPerTimeBucket(String eventType, long from, long to, TimeResolution resolution) {
		logger.info("[AGGREGATION] Getting aggregated metrics for eventType:{} from:{} to:{} in resolution:{}",
			eventType, from, to, resolution.name());
		List<Object[]> rows = switch (resolution) {
			case MINUTE -> processedEventService.aggregateByMinute(eventType, from, to);
			case HOUR -> processedEventService.aggregateByHour(eventType, from, to);
			case DAY ->
				processedEventService.aggregateByDay(eventType, AggregationUtils.toDateTruncatedToDay(from), AggregationUtils.toDateTruncatedToDay(to));
			case WEEK ->
				processedEventService.aggregateByWeek(eventType, AggregationUtils.toDateTruncatedToDay(from), AggregationUtils.toDateTruncatedToDay(to));
			case MONTH ->
				processedEventService.aggregateByMonth(eventType, AggregationUtils.toDateTruncatedToDay(from), AggregationUtils.toDateTruncatedToDay(to));
		};

		logger.info("[AGGREGATION] Retrieving {} rows for eventType:{} from:{} to:{} in resolution:{}",
			rows.size(), eventType, from, to, resolution.name());
		return rows.stream()
		           .map(AggregationUtils::mapRowToBucketMetrics)
		           .collect(Collectors.toList());
	}

	public BucketDetailsDTO getDetailSummary(String eventType, long bucketStart, TimeResolution resolution) {
		List<ProcessedEvent> events = switch (resolution) {
			case MINUTE -> processedEventService.findByEventTypeAndMinuteBucket(eventType, bucketStart);
			case HOUR -> processedEventService.findByEventTypeAndHourBucket(eventType, bucketStart);
			case DAY ->
				processedEventService.findByEventTypeAndDayBucket(eventType, AggregationUtils.toDateTruncatedToDay(bucketStart));
			case WEEK ->
				processedEventService.findByEventTypeAndWeekBucket(eventType, AggregationUtils.toDateTruncatedToDay(bucketStart));
			case MONTH ->
				processedEventService.findByEventTypeAndMonthBucket(eventType, AggregationUtils.toDateTruncatedToDay(bucketStart));
		};

		if (events == null || events.isEmpty()) {
			BucketMetricsDTO emptySummary = new BucketMetricsDTO(bucketStart, 0L, 0.0, 0.0, 0.0, 0L);
			return new BucketDetailsDTO(Collections.emptyList(), emptySummary);
		}

		List<ProcessedEventDTO> dtos = events.stream()
		                                     .map(AggregationUtils::toDto)
		                                     .collect(Collectors.toList());

		BucketMetricsDTO summary = AggregationUtils.computeSummary(bucketStart, events);
		return new BucketDetailsDTO(dtos, summary);
	}
}
