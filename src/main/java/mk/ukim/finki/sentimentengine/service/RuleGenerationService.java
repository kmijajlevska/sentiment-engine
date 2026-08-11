package mk.ukim.finki.sentimentengine.service;


import mk.ukim.finki.sentimentengine.ai.GenAiClient;
import mk.ukim.finki.sentimentengine.ai.GenAiException;
import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.data.entity.SentimentRule;
import mk.ukim.finki.sentimentengine.data.repository.EventTypeRepository;
import mk.ukim.finki.sentimentengine.data.repository.SentimentRuleRepository;
import mk.ukim.finki.sentimentengine.data.service.EventTypeService;
import mk.ukim.finki.sentimentengine.data.service.SentimentRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Service responsible for generating sentiment rules using GenAI.
 *
 * @author kristina
 */
@Service
public class RuleGenerationService {

	private static final Logger log = LoggerFactory.getLogger(RuleGenerationService.class);

	@Value("${rulegen.max-retries:3}")
	private int maxRetries;

	@Value("${rulegen.retry-delay-ms:1000}")
	private long delayMs;

	private static final String PROMPT_TEMPLATE = """
		You are a sentiment analysis expert for an event monitoring system.
		Analyze the following event type and provide a sentiment rule.
		
		Event Type: {eventType}
		Sample payload: {samplePayload}
		
		Based on this event type and sample, provide:
		1. A base sentiment score from -1.0 (very negative) to 1.0 (very positive)
		2. Keywords in payloads that amplify positive sentiment (prefix with +) and negative sentiment (prefix with -)
		3. A brief explanation of your reasoning
		
		Respond in JSON format:
		{
		  "baseScore": <number between -1.0 and 1.0>,
		  "keywords": ["+positive_word", "-negative_word", ...],
		  "explanation": "<one paragraph explanation>"
		}
		""";

	private static final String FALLBACK_JSON = """
		{"baseScore":0.0,"keywords":[],"explanation":"Fallback - AI unavailable"}""";

	private final GenAiClient genAiClient;
	private final SentimentRuleService sentimentRuleService;
	private final EventTypeService eventTypeService;
	private final ObjectMapper objectMapper;

	public RuleGenerationService(GenAiClient genAiClient,
	                             SentimentRuleRepository sentimentRuleRepository,
	                             EventTypeService eventTypeService,
	                             ObjectMapper objectMapper, SentimentRuleService sentimentRuleService) {
		this.genAiClient = genAiClient;
		this.sentimentRuleService = sentimentRuleService;
		this.eventTypeService = eventTypeService;
		this.objectMapper = objectMapper;
	}

	public SentimentRule generateRule(String eventType, String samplePayload) {
		log.info("[RULE-GEN] Generating rule for event type: {}" , eventType);
		String prompt = PROMPT_TEMPLATE
			.replace("{eventType}", eventType)
			.replace("{samplePayload}", samplePayload != null ? samplePayload : "{}");

		String aiResponse = callWithRetry(prompt);

		if (aiResponse == null) {
			// TODO if no AI response, save no rule, or mark the event neutral?
			log.warn("[RULE-GENERATION] AI call failed for eventType: {}, no rule saved", eventType);
			return null;
		}

		double baseScore = 0.0;
		String explanation = "Fallback - AI unavailable";
		String ruleDefinition = FALLBACK_JSON;

		try {
			JsonNode parsed = objectMapper.readTree(aiResponse);
			baseScore = parsed.has("baseScore") ? parsed.get("baseScore").asDouble() : 0.0;
			explanation = parsed.has("explanation") ? parsed.get("explanation").asString() : "No explanation provided";
			ruleDefinition = aiResponse;
		} catch (Exception e) {
			log.warn("[RULE-GENERATION] Failed to parse AI response, using fallback. Error: {}", e.getMessage());
		}

		SentimentRule sentimentRule = sentimentRuleService.findTopByEventTypeOrderByVersionDesc(eventType);
		int nextVersion = sentimentRule != null ? sentimentRule.getVersion() + 1 : 1;


		SentimentRule rule = SentimentRule.builder()
		                                  .eventType(eventType)
		                                  .ruleType("EVENT")
		                                  .ruleDefinition(ruleDefinition)
		                                  .baseScore(baseScore)
		                                  .explanation(explanation)
		                                  .version(nextVersion)
		                                  .build();

		SentimentRule saved = sentimentRuleService.save(rule);

		EventType eventTypeEntity = eventTypeService.findByName(eventType);
		if (eventTypeEntity != null) {
			eventTypeEntity.setHasRule(true);
			eventTypeService.save(eventTypeEntity);
		}

		log.info("[RULE-GENERATION] Generated sentiment rule for eventType:{}', version:{}, baseScore:{}",
			eventType, saved.getVersion(), saved.getBaseScore());

		return saved;
	}

	private String callWithRetry(String prompt) {

		for (int attempt = 1; attempt <= maxRetries; attempt++) {
			try {
				return genAiClient.generateCompletion(prompt);
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

		log.warn("[GEN-AI] All {} GenAI retries exhausted, returning fallback", maxRetries);
		return FALLBACK_JSON;
	}
}
