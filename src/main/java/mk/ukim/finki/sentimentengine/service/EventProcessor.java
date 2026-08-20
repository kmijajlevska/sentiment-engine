package mk.ukim.finki.sentimentengine.service;


import lombok.RequiredArgsConstructor;
import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.data.entity.*;
import mk.ukim.finki.sentimentengine.data.service.ProcessedEventService;
import mk.ukim.finki.sentimentengine.data.service.RawEventService;
import mk.ukim.finki.sentimentengine.data.service.SentimentRuleService;
import mk.ukim.finki.sentimentengine.util.AggregationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author kristina
 */
@Service
@RequiredArgsConstructor
public class EventProcessor {

	private static final Logger logger = LoggerFactory.getLogger(EventProcessor.class);

	private final RawEventService rawEventService;
	private final EventTypeRegistry eventTypeRegistry;
	private final SentimentRuleService sentimentRuleService;
	private final SentimentEvaluationEngine evaluationEngine;
	private final ProcessedEventService processedEventService;
	private final RuleGenerationService ruleGenerationService;
	private final AbsenceDetectionService absenceDetectionService;

	@Value("${rulegen.auto.enabled:true}")
	private boolean autoRuleGenEnabled;

	public void onEvent(EventDTO event) {
		boolean isAbsenceEvent = event.getEventType().startsWith(AbsenceDetectionService.ABSENCE_EVENT_TYPE);

		// check for absence only for basic event types
		if (!isAbsenceEvent) {
			absenceDetectionService.checkForAbsenceOnArrival(event.getEventTimestamp(), event.getEventType());
		}

		RawEvent rawEventEntity = RawEvent.builder()
		                                  .eventType(event.getEventType())
		                                  .timestamp(event.getEventTimestamp())
		                                  .source(event.getSource())
		                                  .payload(event.getPayload())
		                                  .build();

		// Register event type if not known already
		logger.info("[EVENT-PROCESSOR] Processing event of type: {}", event.getEventType());
		if (!eventTypeRegistry.isKnownType(event.getEventType())) {
			eventTypeRegistry.register(event.getEventType(), event.getPayload(), event.getEventTimestamp());
		} else {
			eventTypeRegistry.recordOccurrence(event.getEventType(), event.getEventTimestamp());
		}
		eventTypeRegistry.updateLastSeenPerType(event.getEventType(), event.getEventTimestamp());
		try {
			rawEventEntity = rawEventService.save(rawEventEntity);
			if (!isAbsenceEvent) {
				absenceDetectionService.updateLastReceivedTimestamp(rawEventEntity.getTimestamp());
			}

			SentimentRule sentimentRule = sentimentRuleService.findTopByEventTypeOrderByVersionDesc(rawEventEntity.getEventType());

			if (sentimentRule == null && autoRuleGenEnabled) {
				sentimentRule = ruleGenerationService.generateRule(rawEventEntity.getEventType(), rawEventEntity.getPayload());
			}

			SentimentResult result = evaluationEngine.evaluate(rawEventEntity, sentimentRule);

			// -----
			// Compute time buckets and assign them to processed event
			ProcessedEvent processedEvent = generateProcessedEvent(rawEventEntity, result);
			processedEventService.save(processedEvent);
		} catch (Exception e) {
			logger.warn("[EVENT-PROCESSOR] Sentiment evaluation failed for event {}: {}", rawEventEntity.getId(), e.getMessage());
		}
		event.getMetrics().setFinishedProcessingAt(System.currentTimeMillis());
		logger.info("[PERFORMANCE-LOG] Finished processing event {}, eventType: {}, bufferTookMs: {} processingTookMs: {}",
			event.getId(), event.getEventType(), event.getMetrics().getReceivedAt() - event.getMetrics().getImportedAt(),
			event.getMetrics().getFinishedProcessingAt() - event.getMetrics().getReceivedAt());
	}


	private ProcessedEvent generateProcessedEvent(RawEvent rawEvent, SentimentResult result) {
		long eventTimestamp = rawEvent.getTimestamp();
		long minuteBucket = (eventTimestamp / 60000) * 60000;
		long hourBucket = (eventTimestamp / 3600000) * 3600000;
		Date dayBucket = AggregationUtils.toDateTruncatedToDay(eventTimestamp);
		Date weekBucket = AggregationUtils.toDateTruncatedToWeek(eventTimestamp);
		Date monthBucket = AggregationUtils.toDateTruncatedToMonth(eventTimestamp);

		boolean evaluated = result != null;

		return ProcessedEvent.builder()
		                     .eventId(rawEvent.getId())
		                     .eventType(rawEvent.getEventType())
		                     .eventTimestamp(eventTimestamp)
		                     .sentimentScore(BigDecimal.valueOf(evaluated ? result.score() : 0.0))
		                     .confidence(BigDecimal.valueOf(evaluated ? result.confidence() : 0.0))
		                     .appliedRuleId(evaluated ? result.ruleId() : null)
		                     .evaluationStatus(evaluated ? EvaluationStatus.COMPLETED : EvaluationStatus.PENDING)
		                     .minuteBucket(minuteBucket)
		                     .hourBucket(hourBucket)
		                     .dayBucket(dayBucket)
		                     .weekBucket(weekBucket)
		                     .monthBucket(monthBucket)
		                     .build();
	}

}
