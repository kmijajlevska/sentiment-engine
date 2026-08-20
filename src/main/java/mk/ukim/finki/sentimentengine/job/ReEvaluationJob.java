package mk.ukim.finki.sentimentengine.job;

import mk.ukim.finki.sentimentengine.data.entity.*;
import mk.ukim.finki.sentimentengine.data.service.ProcessedEventService;
import mk.ukim.finki.sentimentengine.data.service.RawEventService;
import mk.ukim.finki.sentimentengine.data.service.SentimentRuleService;
import mk.ukim.finki.sentimentengine.service.EventTypeRegistry;
import mk.ukim.finki.sentimentengine.service.SentimentEvaluationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Scheduled job that re-evaluates PENDING processed events
 * once a sentiment rule becomes available for their event type.
 *
 * @author kristina
 */
@ConditionalOnProperty(name = "reevaluation.job.enabled", havingValue = "true")
@Service
public class ReEvaluationJob {

	private static final Logger logger = LoggerFactory.getLogger(ReEvaluationJob.class);

	private final ProcessedEventService processedEventService;
	private final RawEventService rawEventService;
	private final SentimentRuleService sentimentRuleService;
	private final SentimentEvaluationEngine evaluationEngine;
	private final EventTypeRegistry eventTypeRegistry;

	public ReEvaluationJob(ProcessedEventService processedEventService,
	                       RawEventService rawEventService,
	                       SentimentRuleService sentimentRuleService,
	                       SentimentEvaluationEngine evaluationEngine,
	                       EventTypeRegistry eventTypeRegistry) {
		this.processedEventService = processedEventService;
		this.rawEventService = rawEventService;
		this.sentimentRuleService = sentimentRuleService;
		this.evaluationEngine = evaluationEngine;
		this.eventTypeRegistry = eventTypeRegistry;
	}

	@Scheduled(fixedDelayString = "${reevaluation.job.interval-ms:300000}", initialDelay = 120000L)
	public void reEvaluatePendingEvents() {
		long start = System.currentTimeMillis();
		logger.info("[RE-EVALUATION][JOB] Running the Re-Evaluation scheduled job ..");

		List<EventType> typesWithRules = eventTypeRegistry.getAllEventTypes().stream()
			.filter(EventType::isHasRule)
			.toList();

		int totalReEvaluated = 0;

		for (EventType eventType : typesWithRules) {
			SentimentRule rule = sentimentRuleService.findTopByEventTypeOrderByVersionDesc(eventType.getName());
			if (rule == null) {
				continue;
			}

			List<ProcessedEvent> pendingEvents = processedEventService.findPendingByEventType(eventType.getName());
			if (pendingEvents.isEmpty()) {
				continue;
			}

			logger.info("[RE-EVALUATION][JOB] Found {} pending events for eventType: {}", pendingEvents.size(), eventType.getName());

			for (ProcessedEvent pendingEvent : pendingEvents) {
				try {
					RawEvent rawEvent = rawEventService.findById(pendingEvent.getEventId());
					if (rawEvent == null) {
						logger.warn("[RE-EVALUATION][JOB] Raw event {} not found, skipping", pendingEvent.getEventId());
						continue;
					}

					SentimentResult result = evaluationEngine.evaluate(rawEvent, rule);
					if (result != null) {
						pendingEvent.setSentimentScore(BigDecimal.valueOf(result.score()));
						pendingEvent.setConfidence(BigDecimal.valueOf(result.confidence()));
						pendingEvent.setAppliedRuleId(result.ruleId());
						pendingEvent.setEvaluationStatus(EvaluationStatus.COMPLETED);
						processedEventService.save(pendingEvent);
						totalReEvaluated++;
					}
				} catch (Exception e) {
					logger.warn("[RE-EVALUATION][JOB] Failed to re-evaluate event {}: {}", pendingEvent.getEventId(), e.getMessage());
				}
			}
		}

		logger.info("[RE-EVALUATION][JOB] Finished job for {} events in {} ms",
			totalReEvaluated, System.currentTimeMillis() - start);
	}
}
