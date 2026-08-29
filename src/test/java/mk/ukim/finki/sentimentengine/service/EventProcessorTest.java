package mk.ukim.finki.sentimentengine.service;

import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.data.dto.MetricsDTO;
import mk.ukim.finki.sentimentengine.data.entity.EvaluationStatus;
import mk.ukim.finki.sentimentengine.data.entity.ProcessedEvent;
import mk.ukim.finki.sentimentengine.data.entity.RawEvent;
import mk.ukim.finki.sentimentengine.data.entity.SentimentResult;
import mk.ukim.finki.sentimentengine.data.entity.SentimentRule;
import mk.ukim.finki.sentimentengine.data.service.ProcessedEventService;
import mk.ukim.finki.sentimentengine.data.service.RawEventService;
import mk.ukim.finki.sentimentengine.data.service.SentimentRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EventProcessor} — the central component that coordinates the processing
 * pipeline described in section 3.4.
 *
 * <p>Maps to user scenario 4.1 (Data Import): after import, each event flows through absence check,
 * raw-event persistence, type registration, rule lookup/generation, evaluation and persistence of
 * the processed event. When no rule can be obtained the event is stored as PENDING for later
 * re-evaluation (scenario 4.2).
 */
@ExtendWith(MockitoExtension.class)
class EventProcessorTest {

	@Mock
	private RawEventService rawEventService;
	@Mock
	private EventTypeRegistry eventTypeRegistry;
	@Mock
	private SentimentRuleService sentimentRuleService;
	@Mock
	private SentimentEvaluationEngine evaluationEngine;
	@Mock
	private ProcessedEventService processedEventService;
	@Mock
	private RuleGenerationService ruleGenerationService;
	@Mock
	private AbsenceDetectionService absenceDetectionService;

	private EventProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new EventProcessor(rawEventService, eventTypeRegistry, sentimentRuleService,
			evaluationEngine, processedEventService, ruleGenerationService, absenceDetectionService);
		ReflectionTestUtils.setField(processor, "autoRuleGenEnabled", true);
	}

	private EventDTO event(String type) {
		EventDTO dto = new EventDTO();
		dto.setId(UUID.randomUUID());
		dto.setEventType(type);
		dto.setEventTimestamp(1700000000000L);
		dto.setSource("octocat/hello-world");
		dto.setPayload("{\"a\":1}");
		MetricsDTO metrics = new MetricsDTO();
		metrics.setImportedAt(1L);
		metrics.setReceivedAt(2L);
		dto.setMetrics(metrics);
		return dto;
	}

	private RawEvent savedRawEvent(String type) {
		RawEvent raw = RawEvent.builder().eventType(type).timestamp(1700000000000L).source("s").payload("{\"a\":1}").build();
		raw.setId(99L);
		return raw;
	}

	@Test
	@DisplayName("A known event with an existing rule is evaluated and stored as COMPLETED")
	void knownEventWithRuleIsStoredCompleted() {
		EventDTO dto = event("PushEvent");
		when(eventTypeRegistry.isKnownType("PushEvent")).thenReturn(true);
		when(rawEventService.save(any(RawEvent.class))).thenReturn(savedRawEvent("PushEvent"));
		SentimentRule rule = SentimentRule.builder().eventType("PushEvent").version(1).build();
		rule.setId(7L);
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(rule);
		when(evaluationEngine.evaluate(any(RawEvent.class), eq(rule)))
			.thenReturn(new SentimentResult(0.5, 7L, 0.8));

		processor.onEvent(dto);

		ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
		verify(processedEventService).save(captor.capture());
		ProcessedEvent saved = captor.getValue();
		assertThat(saved.getEvaluationStatus()).isEqualTo(EvaluationStatus.COMPLETED);
		assertThat(saved.getAppliedRuleId()).isEqualTo(7L);
		assertThat(saved.getSentimentScore().doubleValue()).isEqualTo(0.5);
		// existing rule -> no AI generation
		verifyNoInteractions(ruleGenerationService);
	}

	@Test
	@DisplayName("A new event type is registered and triggers inline rule generation")
	void newEventTypeIsRegisteredAndTriggersGeneration() {
		EventDTO dto = event("BrandNewEvent");
		when(eventTypeRegistry.isKnownType("BrandNewEvent")).thenReturn(false);
		when(rawEventService.save(any(RawEvent.class))).thenReturn(savedRawEvent("BrandNewEvent"));
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("BrandNewEvent")).thenReturn(null);
		SentimentRule generated = SentimentRule.builder().eventType("BrandNewEvent").version(1).build();
		generated.setId(11L);
		when(ruleGenerationService.generateRule(eq("BrandNewEvent"), anyString())).thenReturn(generated);
		when(evaluationEngine.evaluate(any(RawEvent.class), eq(generated)))
			.thenReturn(new SentimentResult(0.1, 11L, 0.0));

		processor.onEvent(dto);

		verify(eventTypeRegistry).register(eq("BrandNewEvent"), anyString(), anyLong());
		verify(ruleGenerationService).generateRule(eq("BrandNewEvent"), anyString());
		verify(processedEventService).save(any(ProcessedEvent.class));
	}

	@Test
	@DisplayName("When no rule can be obtained the event is stored as PENDING with a zero score")
	void eventStoredPendingWhenNoRule() {
		EventDTO dto = event("PushEvent");
		when(eventTypeRegistry.isKnownType("PushEvent")).thenReturn(true);
		when(rawEventService.save(any(RawEvent.class))).thenReturn(savedRawEvent("PushEvent"));
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(null);
		when(ruleGenerationService.generateRule(eq("PushEvent"), anyString())).thenReturn(null);
		when(evaluationEngine.evaluate(any(RawEvent.class), isNull())).thenReturn(null);

		processor.onEvent(dto);

		ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
		verify(processedEventService).save(captor.capture());
		ProcessedEvent saved = captor.getValue();
		assertThat(saved.getEvaluationStatus()).isEqualTo(EvaluationStatus.PENDING);
		assertThat(saved.getAppliedRuleId()).isNull();
		assertThat(saved.getSentimentScore().doubleValue()).isEqualTo(0.0);
	}

	@Test
	@DisplayName("Absence is checked on arrival for normal events")
	void absenceCheckedOnArrivalForNormalEvents() {
		EventDTO dto = event("PushEvent");
		when(eventTypeRegistry.isKnownType("PushEvent")).thenReturn(true);
		when(rawEventService.save(any(RawEvent.class))).thenReturn(savedRawEvent("PushEvent"));
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(null);
		when(ruleGenerationService.generateRule(anyString(), anyString())).thenReturn(null);

		processor.onEvent(dto);

		verify(absenceDetectionService).checkForAbsenceOnArrival(dto.getEventTimestamp(), "PushEvent");
		verify(absenceDetectionService).updateLastReceivedTimestamp(anyLong());
	}

	@Test
	@DisplayName("Absence-type events skip the on-arrival absence check and last-received update")
	void absenceEventsSkipAbsenceCheck() {
		EventDTO dto = event(AbsenceDetectionService.GLOBAL_ABSENCE_EVENT_TYPE);
		when(eventTypeRegistry.isKnownType(anyString())).thenReturn(true);
		when(rawEventService.save(any(RawEvent.class)))
			.thenReturn(savedRawEvent(AbsenceDetectionService.GLOBAL_ABSENCE_EVENT_TYPE));
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc(anyString())).thenReturn(null);
		when(ruleGenerationService.generateRule(anyString(), anyString())).thenReturn(null);

		processor.onEvent(dto);

		verify(absenceDetectionService, never()).checkForAbsenceOnArrival(anyLong(), anyString());
		verify(absenceDetectionService, never()).updateLastReceivedTimestamp(anyLong());
	}
}
