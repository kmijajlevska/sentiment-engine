package mk.ukim.finki.sentimentengine.service;

import mk.ukim.finki.sentimentengine.ai.GenAiClient;
import mk.ukim.finki.sentimentengine.ai.GenAiException;
import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.data.entity.SentimentRule;
import mk.ukim.finki.sentimentengine.data.service.EventTypeService;
import mk.ukim.finki.sentimentengine.data.service.SentimentRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RuleGenerationService}.
 *
 * <p>Maps to user scenarios 4.1 (Data Import — a rule is generated automatically for a new event
 * type) and 4.2 (Rule Management — AI regeneration creates a new version). The service must call
 * the AI provider, parse the structured response into a versioned rule, and mark the event type as
 * having a rule. On repeated AI failures it must give up and return null so the event stays PENDING.
 */
@ExtendWith(MockitoExtension.class)
class RuleGenerationServiceTest {

	@Mock
	private GenAiClient genAiClient;
	@Mock
	private SentimentRuleService sentimentRuleService;
	@Mock
	private EventTypeService eventTypeService;

	private RuleGenerationService service;

	private static final String AI_JSON = """
		{
		  "baseScore": 0.6,
		  "keywords": ["+merged", "-reverted"],
		  "explanation": "Push events are generally positive."
		}
		""";

	@BeforeEach
	void setUp() {
		service = new RuleGenerationService(genAiClient, sentimentRuleService, eventTypeService, new ObjectMapper());
		ReflectionTestUtils.setField(service, "maxRetries", 3);
		ReflectionTestUtils.setField(service, "delayMs", 1L);
	}

	@Test
	@DisplayName("Generates and persists a version-1 rule for a new event type")
	void generatesFirstVersionRule() {
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(null);
		when(genAiClient.generateCompletion(anyString())).thenReturn(AI_JSON);
		when(sentimentRuleService.save(any(SentimentRule.class))).thenAnswer(inv -> inv.getArgument(0));
		EventType eventType = EventType.builder().name("PushEvent").hasRule(false).build();
		when(eventTypeService.findByName("PushEvent")).thenReturn(eventType);

		SentimentRule result = service.generateRule("PushEvent", "{\"type\":\"PushEvent\"}");

		assertThat(result).isNotNull();
		assertThat(result.getEventType()).isEqualTo("PushEvent");
		assertThat(result.getVersion()).isEqualTo(1);
		assertThat(result.getBaseScore()).isEqualTo(0.6);
		assertThat(result.getExplanation()).contains("positive");

		// event type gets flagged as having a rule
		assertThat(eventType.isHasRule()).isTrue();
		verify(eventTypeService).save(eventType);
	}

	@Test
	@DisplayName("Regeneration (force) creates the next version instead of replacing")
	void regenerationCreatesNextVersion() {
		SentimentRule existing = SentimentRule.builder().eventType("PushEvent").version(2).build();
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(existing);
		when(genAiClient.generateCompletion(anyString())).thenReturn(AI_JSON);
		when(sentimentRuleService.save(any(SentimentRule.class))).thenAnswer(inv -> inv.getArgument(0));

		SentimentRule result = service.generateRule("PushEvent", "{}", true);

		assertThat(result).isNotNull();
		assertThat(result.getVersion()).isEqualTo(3);
	}

	@Test
	@DisplayName("Skips AI call when a rule already exists and force is not set")
	void skipsGenerationWhenRuleAlreadyExists() {
		SentimentRule existing = SentimentRule.builder().eventType("PushEvent").version(1).build();
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(existing);

		SentimentRule result = service.generateRule("PushEvent", "{}");

		assertThat(result).isSameAs(existing);
		verifyNoInteractions(genAiClient);
		verify(sentimentRuleService, never()).save(any());
	}

	@Test
	@DisplayName("Strips markdown code fences from the AI response before parsing")
	void stripsMarkdownFences() {
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(null);
		when(genAiClient.generateCompletion(anyString())).thenReturn("```json\n" + AI_JSON + "\n```");
		when(sentimentRuleService.save(any(SentimentRule.class))).thenAnswer(inv -> inv.getArgument(0));
		when(eventTypeService.findByName("PushEvent")).thenReturn(null);

		SentimentRule result = service.generateRule("PushEvent", "{}");

		assertThat(result).isNotNull();
		assertThat(result.getBaseScore()).isEqualTo(0.6);
		ArgumentCaptor<SentimentRule> captor = ArgumentCaptor.forClass(SentimentRule.class);
		verify(sentimentRuleService).save(captor.capture());
		// the persisted definition must be clean JSON (no backticks)
		assertThat(captor.getValue().getRuleDefinition()).doesNotContain("`");
	}

	@Test
	@DisplayName("Retries the AI call and succeeds on a later attempt")
	void retriesAndSucceeds() {
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(null);
		when(genAiClient.generateCompletion(anyString()))
			.thenThrow(new GenAiException("temporary"))
			.thenReturn(AI_JSON);
		when(sentimentRuleService.save(any(SentimentRule.class))).thenAnswer(inv -> inv.getArgument(0));
		when(eventTypeService.findByName("PushEvent")).thenReturn(null);

		SentimentRule result = service.generateRule("PushEvent", "{}");

		assertThat(result).isNotNull();
		verify(genAiClient, times(2)).generateCompletion(anyString());
	}

	@Test
	@DisplayName("Returns null after all retries are exhausted so the event stays PENDING")
	void returnsNullWhenAllRetriesFail() {
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(null);
		when(genAiClient.generateCompletion(anyString())).thenThrow(new GenAiException("down"));

		SentimentRule result = service.generateRule("PushEvent", "{}");

		assertThat(result).isNull();
		verify(genAiClient, times(3)).generateCompletion(anyString());
		verify(sentimentRuleService, never()).save(any());
	}
}
