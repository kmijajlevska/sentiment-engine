package mk.ukim.finki.sentimentengine.service;


import mk.ukim.finki.sentimentengine.ai.GenAiClient;
import mk.ukim.finki.sentimentengine.ai.GenAiException;
import mk.ukim.finki.sentimentengine.data.dto.GenAiRuleResponse;
import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.data.entity.SentimentRule;
import mk.ukim.finki.sentimentengine.data.service.EventTypeService;
import mk.ukim.finki.sentimentengine.data.service.SentimentRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static mk.ukim.finki.sentimentengine.util.RuleGenerationUtils.FALLBACK_JSON;
import static mk.ukim.finki.sentimentengine.util.RuleGenerationUtils.PROMPT_TEMPLATE;

/**
 * Service responsible for generating sentiment rules using GenAI.
 *
 * @author kristina
 */
@Service
public class RuleGenerationService {

	private static final Logger log = LoggerFactory.getLogger(RuleGenerationService.class);

	private final ConcurrentHashMap<String, ReentrantLock> eventTypeLocks = new ConcurrentHashMap<>();
	private final GenAiClient genAiClient;
	private final SentimentRuleService sentimentRuleService;
	private final EventTypeService eventTypeService;
	private final ObjectMapper objectMapper;
	@Value("${rulegen.max-retries:3}")
	private int maxRetries;
	@Value("${rulegen.retry-delay-ms:1000}")
	private long delayMs;

	public RuleGenerationService(GenAiClient genAiClient, EventTypeService eventTypeService,
	                             ObjectMapper objectMapper, SentimentRuleService sentimentRuleService) {
		this.genAiClient = genAiClient;
		this.sentimentRuleService = sentimentRuleService;
		this.eventTypeService = eventTypeService;
		this.objectMapper = objectMapper;
	}

	public SentimentRule generateRule(String eventType, String samplePayload) {
		ReentrantLock lock = eventTypeLocks.computeIfAbsent(eventType, k -> new ReentrantLock());
		try {
			if (!lock.tryLock(30, TimeUnit.SECONDS)) {
				log.warn("[RULE-GEN] Timed out waiting for rule generation lock for eventType: {}. Skipping.", eventType);
				return null;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("[RULE-GEN] Interrupted while waiting for lock for eventType: {}", eventType);
			return null;
		}

		try {
			// check if another thread generated the rule while waiting
			SentimentRule existing = sentimentRuleService.findTopByEventTypeOrderByVersionDesc(eventType);
			if (existing != null) {
				log.info("[RULE-GEN] Rule already exists for eventType: {}, version: {}. Skipping generation.",
					eventType, existing.getVersion());
				return existing;
			}

			log.info("[RULE-GEN] Generating rule for event type: {}", eventType);
			String prompt = PROMPT_TEMPLATE
				.replace("{eventType}", eventType)
				.replace("{samplePayload}", samplePayload != null ? samplePayload : "{}");

			String aiResponse = callWithRetry(prompt);
			GenAiRuleResponse ruleResponse = objectMapper.readValue(aiResponse, GenAiRuleResponse.class);

			SentimentRule sentimentRule = sentimentRuleService.findTopByEventTypeOrderByVersionDesc(eventType);
			int nextVersion = sentimentRule != null ? sentimentRule.getVersion() + 1 : 1;

			SentimentRule rule = SentimentRule.builder()
			                                  .eventType(eventType)
			                                  .ruleType("EVENT")
			                                  .ruleDefinition(aiResponse)
			                                  .baseScore(ruleResponse.getBaseScore())
			                                  .explanation(ruleResponse.getExplanation())
			                                  .version(nextVersion)
			                                  .build();

			SentimentRule saved = sentimentRuleService.save(rule);

			EventType eventTypeEntity = eventTypeService.findByName(eventType);
			if (eventTypeEntity != null) {
				eventTypeEntity.setHasRule(true);
				eventTypeService.save(eventTypeEntity);
			}

			log.info("[RULE-GEN] Generated sentiment rule for eventType: {}, version: {}, baseScore: {}",
				eventType, saved.getVersion(), saved.getBaseScore());

			return saved;
		} catch (Exception e) {
			log.warn("[RULE-GEN] Rule generation failed for eventType: {}. Error: {}", eventType, e.getMessage());
			return null;
		} finally {
			lock.unlock();
		}
	}

	private String callWithRetry(String prompt) {

		for (int attempt = 1; attempt <= maxRetries; attempt++) {
			try {
				String aiResponse = genAiClient.generateCompletion(prompt);
				if (aiResponse != null) {
					return aiResponse.replaceAll("(?s)^```(?:json)?\\s*|\\s*```$", "").strip();
				}
			} catch (GenAiException e) {
				log.warn("[GEN-AI] GenAI call attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
				if (attempt < maxRetries) {
					try {
						Thread.sleep(delayMs * attempt); // exponential backoff
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}

		log.warn("[GEN-AI] All {} GenAI retries exhausted, returning null", maxRetries);
		// return FALLBACK_JSON;
		return null;
	}
}
