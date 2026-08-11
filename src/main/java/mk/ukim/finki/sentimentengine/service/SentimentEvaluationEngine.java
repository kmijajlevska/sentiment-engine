package mk.ukim.finki.sentimentengine.service;


import mk.ukim.finki.sentimentengine.data.entity.RawEvent;
import mk.ukim.finki.sentimentengine.data.entity.SentimentResult;
import mk.ukim.finki.sentimentengine.data.entity.SentimentRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Evaluates an event against a sentiment rule and produces a scored result.
 *
 * @author kristina
 */
@Service
public class SentimentEvaluationEngine {

	private static final Logger logger = LoggerFactory.getLogger(SentimentEvaluationEngine.class);
	private final ObjectMapper objectMapper;

	public SentimentEvaluationEngine(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}


	public SentimentResult evaluate(RawEvent event, SentimentRule rule) {
		if (rule == null) {
			logger.warn("[SENTIMENT-EVALUATION] No rule exists for eventType: {}, returning score: 0.0", event.getEventType());
			return new SentimentResult(0.0, null, 0.0);
		}

		double baseScore = rule.getBaseScore();

		// parse rule definition JSON
		Map<String, Object> definition;
		try {
			String ruleDefinition = rule.getRuleDefinition();
			if (ruleDefinition == null || ruleDefinition.isBlank()) {
				// no keywords to adjust the score, return base score from rule
				logger.warn("[SENTIMENT-EVALUATION] Rule definition is null for rule {}, returning base score: {}", rule.getId(), baseScore);
				return new SentimentResult(Math.clamp(baseScore, -1.0, 1.0), rule.getId(), 0.0);
			}
			definition = objectMapper.readValue(ruleDefinition, new TypeReference<>() {});

		} catch (Exception e) {
			// error while parsing the rule definition, return base score
			logger.error("[SENTIMENT-EVALUATION] Failed to parse rule definition for rule {}, returning base score: {}, {}", rule.getId(), baseScore, e.getMessage());
			return new SentimentResult(Math.clamp(baseScore, -1.0, 1.0), rule.getId(), 0.0);
		}

		// extract keywords list
		List<String> keywords = extractKeywords(definition);
		if (keywords.isEmpty()) {
			logger.warn("[SENTIMENT-EVALUATION] No keywords present for rule {}, returning base score: {}", rule.getId(), baseScore);
			return new SentimentResult(Math.clamp(baseScore, -1.0, 1.0), rule.getId(), 0.0);
		}


		String payload = event.getPayload();
		if (payload == null || payload.isBlank()) {
			logger.warn("[SENTIMENT-EVALUATION] No event payload for event {}, returning base score: {}", event.getId(), baseScore);
			return new SentimentResult(Math.clamp(baseScore, -1.0, 1.0), rule.getId(), 0.0);
		} // maybe group all base-score scenarios in one condition instead?
		String payloadLower = payload.toLowerCase();

		// count keyword matches
		int positiveHits = 0;
		int negativeHits = 0;

		for (String keyword : keywords) {
			if (keyword.startsWith("+")) {
				String term = keyword.substring(1).toLowerCase();
				if (!term.isEmpty() && payloadLower.contains(term)) {
					positiveHits++;
				}
			} else if (keyword.startsWith("-")) {
				String term = keyword.substring(1).toLowerCase();
				if (!term.isEmpty() && payloadLower.contains(term)) {
					negativeHits++;
				}
			}
		}

		// compute score
		double score = baseScore + (positiveHits * 0.1) - (negativeHits * 0.1);
		double clampedScore = Math.clamp(score, -1.0, 1.0);

		// compute confidence, may be removed later if not used
		double confidence = (double) (positiveHits + negativeHits) / keywords.size();
		logger.info("[SENTIMENT-EVALUATION] Evaluated sentiment for event {} of type: {}, returning score: {}", event.getId(), event.getEventType(), clampedScore);
		return new SentimentResult(clampedScore, rule.getId(), confidence);
	}

	@SuppressWarnings("unchecked")
	private List<String> extractKeywords(Map<String, Object> definition) {
		Object keywords = definition.get("keywords");
		return keywords instanceof List<?> list ? (List<String>) list : Collections.emptyList();
	}

}
