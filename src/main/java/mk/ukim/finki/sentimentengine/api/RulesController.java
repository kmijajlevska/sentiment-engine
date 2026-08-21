package mk.ukim.finki.sentimentengine.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mk.ukim.finki.sentimentengine.data.dto.PendingCountDTO;
import mk.ukim.finki.sentimentengine.data.dto.RuleDetailDTO;
import mk.ukim.finki.sentimentengine.data.dto.RuleListItemDTO;
import mk.ukim.finki.sentimentengine.data.dto.RuleRequestDTO;
import mk.ukim.finki.sentimentengine.data.entity.*;
import mk.ukim.finki.sentimentengine.data.service.EventTypeService;
import mk.ukim.finki.sentimentengine.data.service.ProcessedEventService;
import mk.ukim.finki.sentimentengine.data.service.RawEventService;
import mk.ukim.finki.sentimentengine.data.service.SentimentRuleService;
import mk.ukim.finki.sentimentengine.service.RuleGenerationService;
import mk.ukim.finki.sentimentengine.service.SentimentEvaluationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author kristina
 */
@RestController
@RequestMapping("/rules")
@Tag(name = "Rules", description = "Sentiment rule management")
@RequiredArgsConstructor
public class RulesController {

	private static final Logger logger = LoggerFactory.getLogger(RulesController.class);

	private final SentimentRuleService sentimentRuleService;
	private final ProcessedEventService processedEventService;
	private final RawEventService rawEventService;
	private final SentimentEvaluationEngine evaluationEngine;
	private final EventTypeService eventTypeService;
	private final RuleGenerationService ruleGenerationService;
	private final ObjectMapper objectMapper;

	@GetMapping
	@Operation(summary = "List all rules with assigned and pending counts")
	public ResponseEntity<List<RuleListItemDTO>> listAllRules() {
		List<SentimentRule> rules = sentimentRuleService.findAllByOrderByCreatedAtDesc();

		// pending grouped by event type
		Map<String, Long> pendingCountMap = new HashMap<>();
		for (Object[] row : processedEventService.countPendingByEventType()) {
			String eventType = (String) row[0];
			Long count = (Long) row[1];
			pendingCountMap.put(eventType, count);
		}

		//  assigned grouped by rule
		Map<Long, Long> assignedCountMap = new HashMap<>();
		for (Object[] row : processedEventService.countAssignedByRuleIdGrouped()) {
			Long ruleId = (Long) row[0];
			Long count = (Long) row[1];
			assignedCountMap.put(ruleId, count);
		}

		// map results
		List<RuleListItemDTO> result = rules.stream()
		                                    .map(rule -> new RuleListItemDTO(
												rule.getId(),
												rule.getEventType(),
												rule.getRuleType(),
												rule.getBaseScore(),
												rule.getVersion(),
												rule.getExplanation(),
												rule.getCreatedAt(),
												assignedCountMap.getOrDefault(rule.getId(), 0L),
												pendingCountMap.getOrDefault(rule.getEventType(), 0L)
											))
		                                    .toList();

		return ResponseEntity.ok(result);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get rule details")
	public ResponseEntity<?> getRuleById(@PathVariable Long id) {
		SentimentRule rule = sentimentRuleService.findById(id);
		if (rule == null) {
			return ResponseEntity.status(404).body(Map.of("error", "No rule found with id: " + id));
		}
		RuleDetailDTO dto = new RuleDetailDTO(
			rule.getId(),
			rule.getEventType(),
			rule.getRuleType(),
			rule.getRuleDefinition(),
			rule.getBaseScore(),
			rule.getExplanation(),
			rule.getVersion(),
			rule.getCreatedAt()
		);
		return ResponseEntity.ok(dto);
	}

	@PostMapping
	@Operation(summary = "Create new rule")
	public ResponseEntity<?> createRule(@RequestBody RuleRequestDTO request) {
		// Validate ruleDefinition is valid JSON
		if (request.ruleDefinition() != null) {
			try {
				objectMapper.readTree(request.ruleDefinition());
			} catch (Exception e) {
				return ResponseEntity.badRequest().body(Map.of("error", "ruleDefinition must contain valid JSON"));
			}
		}

		// Validate baseScore in [-1.0, 1.0]
		if (request.baseScore() < -1.0 || request.baseScore() > 1.0) {
			return ResponseEntity.badRequest().body(Map.of("error", "baseScore must be between -1.0 and 1.0"));
		}

		// Validate eventType exists
		EventType eventType = eventTypeService.findByName(request.eventType());
		if (eventType == null) {
			return ResponseEntity.badRequest().body(Map.of("error", "Event type '" + request.eventType() + "' is not registered"));
		}

		// Compute next version
		SentimentRule latest = sentimentRuleService.findTopByEventTypeOrderByVersionDesc(request.eventType());
		int nextVersion = (latest != null) ? latest.getVersion() + 1 : 1;

		// Build and save entity
		SentimentRule rule = SentimentRule.builder()
		                                  .eventType(request.eventType())
		                                  .ruleType(request.ruleType() != null ? request.ruleType() : "EVENT")
		                                  .ruleDefinition(request.ruleDefinition())
		                                  .baseScore(request.baseScore())
		                                  .explanation(request.explanation())
		                                  .version(nextVersion)
		                                  .build();

		SentimentRule saved = sentimentRuleService.save(rule);

		// Set EventType.hasRule = true
		eventType.setHasRule(true);
		eventTypeService.save(eventType);

		RuleDetailDTO dto = new RuleDetailDTO(
			saved.getId(),
			saved.getEventType(),
			saved.getRuleType(),
			saved.getRuleDefinition(),
			saved.getBaseScore(),
			saved.getExplanation(),
			saved.getVersion(),
			saved.getCreatedAt()
		);
		return ResponseEntity.status(201).body(dto);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Edit rule")
	public ResponseEntity<?> updateRule(@PathVariable Long id, @RequestBody RuleRequestDTO request) {
		SentimentRule rule = sentimentRuleService.findById(id);
		if (rule == null) {
			return ResponseEntity.status(404).body(Map.of("error", "No rule found with id: " + id));
		}

		// Validate ruleDefinition is valid JSON
		if (request.ruleDefinition() != null) {
			try {
				objectMapper.readTree(request.ruleDefinition());
			} catch (Exception e) {
				return ResponseEntity.badRequest().body(Map.of("error", "ruleDefinition must contain valid JSON"));
			}
		}

		// Validate baseScore in [-1.0, 1.0]
		if (request.baseScore() < -1.0 || request.baseScore() > 1.0) {
			return ResponseEntity.badRequest().body(Map.of("error", "baseScore must be between -1.0 and 1.0"));
		}

		// Update only mutable fields
		if (request.ruleType() != null) {
			rule.setRuleType(request.ruleType());
		}
		if (request.ruleDefinition() != null) {
			rule.setRuleDefinition(request.ruleDefinition());
		}
		rule.setBaseScore(request.baseScore());
		if (request.explanation() != null) {
			rule.setExplanation(request.explanation());
		}

		SentimentRule saved = sentimentRuleService.save(rule);

		RuleDetailDTO dto = new RuleDetailDTO(
			saved.getId(),
			saved.getEventType(),
			saved.getRuleType(),
			saved.getRuleDefinition(),
			saved.getBaseScore(),
			saved.getExplanation(),
			saved.getVersion(),
			saved.getCreatedAt()
		);
		return ResponseEntity.ok(dto);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete rule and update hasRule flag")
	public ResponseEntity<?> deleteRule(@PathVariable Long id) {
		SentimentRule rule = sentimentRuleService.findById(id);
		if (rule == null) {
			return ResponseEntity.status(404).body(Map.of("error", "No rule found with id: " + id));
		}

		String eventType = rule.getEventType();

		try {
			sentimentRuleService.deleteById(id);

			List<SentimentRule> remainingRules = sentimentRuleService.findByEventType(eventType);
			if (remainingRules.isEmpty()) {
				EventType et = eventTypeService.findByName(eventType);
				if (et != null) {
					et.setHasRule(false);
					eventTypeService.save(et);
				}
			}

			return ResponseEntity.noContent().build();
		} catch (Exception e) {
			logger.error("[API][RULES] Failed to delete rule with id {}: {}", id, e.getMessage());
			return ResponseEntity.status(500).body(Map.of("error", "Deletion could not be completed"));
		}
	}

	@GetMapping("/pending-counts")
	@Operation(summary = "Get pending event counts grouped by event type")
	public ResponseEntity<List<PendingCountDTO>> getPendingCounts() {
		List<PendingCountDTO> result = processedEventService.countPendingByEventType().stream()
		                                                    .map(row -> new PendingCountDTO((String) row[0], (Long) row[1]))
		                                                    .toList();
		return ResponseEntity.ok(result);
	}

	@PostMapping("/regenerate/{eventType}")
	@Operation(summary = "Regenerate rule using AI (creates new version)")
	public ResponseEntity<?> regenerateRule(@PathVariable String eventType) {
		EventType eventTypeEntity = eventTypeService.findByName(eventType);
		if (eventTypeEntity == null) {
			return ResponseEntity.status(404).body(Map.of("error", "Event type '" + eventType + "' is not recognized"));
		}

		SentimentRule generated = ruleGenerationService.generateRule(eventType, eventTypeEntity.getSamplePayloadSchema(), true);
		if (generated == null) {
			return ResponseEntity.status(503).body(Map.of("error", "Rule generation failed after retries"));
		}

		RuleDetailDTO dto = new RuleDetailDTO(
			generated.getId(),
			generated.getEventType(),
			generated.getRuleType(),
			generated.getRuleDefinition(),
			generated.getBaseScore(),
			generated.getExplanation(),
			generated.getVersion(),
			generated.getCreatedAt()
		);
		return ResponseEntity.ok(dto);
	}

	@PostMapping("/reevaluate/{eventType}")
	@Operation(summary = "Re-evaluate pending events for an event type")
	public ResponseEntity<String> reEvaluateByEventType(@PathVariable String eventType) {
		SentimentRule rule = sentimentRuleService.findTopByEventTypeOrderByVersionDesc(eventType);
		if (rule == null) {
			return ResponseEntity.badRequest().body("No rule exists for event type: " + eventType);
		}

		List<ProcessedEvent> pendingEvents = processedEventService.findPendingByEventType(eventType);
		if (pendingEvents.isEmpty()) {
			return ResponseEntity.ok("No pending events for event type: " + eventType);
		}

		logger.info("[API][RULES] Manually re-evaluating {} pending events for eventType: {}", pendingEvents.size(), eventType);

		int reEvaluated = 0;
		for (ProcessedEvent pendingEvent : pendingEvents) {
			try {
				RawEvent rawEvent = rawEventService.findById(pendingEvent.getEventId());
				if (rawEvent == null) {
					continue;
				}

				SentimentResult result = evaluationEngine.evaluate(rawEvent, rule);
				if (result != null) {
					pendingEvent.setSentimentScore(java.math.BigDecimal.valueOf(result.score()));
					pendingEvent.setConfidence(java.math.BigDecimal.valueOf(result.confidence()));
					pendingEvent.setAppliedRuleId(result.ruleId());
					pendingEvent.setEvaluationStatus(EvaluationStatus.COMPLETED);
					processedEventService.save(pendingEvent);
					reEvaluated++;
				}
			} catch (Exception e) {
				logger.warn("[API][RULES] Failed to re-evaluate event {}: {}", pendingEvent.getEventId(), e.getMessage());
			}
		}

		return ResponseEntity.ok("Re-evaluated " + reEvaluated + " events for event type: " + eventType);
	}
}
