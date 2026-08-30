package mk.ukim.finki.sentimentengine.api;

import mk.ukim.finki.sentimentengine.data.entity.EvaluationStatus;
import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.data.entity.ProcessedEvent;
import mk.ukim.finki.sentimentengine.data.entity.RawEvent;
import mk.ukim.finki.sentimentengine.data.entity.SentimentResult;
import mk.ukim.finki.sentimentengine.data.entity.SentimentRule;
import mk.ukim.finki.sentimentengine.data.service.EventTypeService;
import mk.ukim.finki.sentimentengine.data.service.ProcessedEventService;
import mk.ukim.finki.sentimentengine.data.service.RawEventService;
import mk.ukim.finki.sentimentengine.data.service.SentimentRuleService;
import mk.ukim.finki.sentimentengine.service.RuleGenerationService;
import mk.ukim.finki.sentimentengine.service.SentimentEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link RulesController} using the MVC slice.
 *
 * <p>Maps to user scenario 4.2 (Rule Management): listing rules with pending indicators, viewing
 * detail, manual create/edit with validation (base score range, valid JSON, registered event
 * type), AI regeneration creating a new version, and re-evaluation of PENDING events.
 */
@WebMvcTest(RulesController.class)
class RulesControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SentimentRuleService sentimentRuleService;
	@MockitoBean
	private ProcessedEventService processedEventService;
	@MockitoBean
	private RawEventService rawEventService;
	@MockitoBean
	private SentimentEvaluationService sentimentEvaluationService;
	@MockitoBean
	private EventTypeService eventTypeService;
	@MockitoBean
	private RuleGenerationService ruleGenerationService;

	private SentimentRule rule(long id, String eventType, double baseScore, int version) {
		SentimentRule r = SentimentRule.builder()
		                               .eventType(eventType)
		                               .ruleType("EVENT")
		                               .ruleDefinition("{\"keywords\":[\"+merged\"]}")
		                               .baseScore(baseScore)
		                               .explanation("because")
		                               .version(version)
		                               .build();
		r.setId(id);
		return r;
	}

	@Test
	@DisplayName("GET /rules lists rules with assigned and pending counts")
	void listAllRules() throws Exception {
		when(sentimentRuleService.findAllByOrderByCreatedAtDesc())
			.thenReturn(List.of(rule(1L, "PushEvent", 0.3, 1)));
		when(processedEventService.countPendingByEventType())
			.thenReturn(List.<Object[]>of(new Object[]{"PushEvent", 4L}));
		when(processedEventService.countAssignedByRuleIdGrouped())
			.thenReturn(List.<Object[]>of(new Object[]{1L, 10L}));

		mockMvc.perform(get("/rules"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$[0].eventType").value("PushEvent"))
		       .andExpect(jsonPath("$[0].assignedEventCount").value(10))
		       .andExpect(jsonPath("$[0].pendingEventCount").value(4));
	}

	@Test
	@DisplayName("GET /rules/{id} returns rule detail")
	void getRuleById() throws Exception {
		when(sentimentRuleService.findById(1L)).thenReturn(rule(1L, "PushEvent", 0.3, 2));

		mockMvc.perform(get("/rules/1"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.id").value(1))
		       .andExpect(jsonPath("$.eventType").value("PushEvent"))
		       .andExpect(jsonPath("$.version").value(2));
	}

	@Test
	@DisplayName("GET /rules/{id} returns 404 when the rule does not exist")
	void getRuleByIdNotFound() throws Exception {
		when(sentimentRuleService.findById(99L)).thenReturn(null);

		mockMvc.perform(get("/rules/99"))
		       .andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("POST /rules creates a new rule for a registered event type")
	void createRuleSuccess() throws Exception {
		when(eventTypeService.findByName("PushEvent"))
			.thenReturn(EventType.builder().name("PushEvent").hasRule(false).build());
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(null);
		when(sentimentRuleService.save(any(SentimentRule.class))).thenAnswer(inv -> {
			SentimentRule r = inv.getArgument(0);
			r.setId(5L);
			return r;
		});

		String body = """
			{"eventType":"PushEvent","ruleType":"EVENT","ruleDefinition":"{\\"keywords\\":[\\"+merged\\"]}","baseScore":0.5,"explanation":"x"}
			""";

		mockMvc.perform(post("/rules").contentType(MediaType.APPLICATION_JSON).content(body))
		       .andExpect(status().isCreated())
		       .andExpect(jsonPath("$.eventType").value("PushEvent"))
		       .andExpect(jsonPath("$.baseScore").value(0.5));

		verify(sentimentRuleService).save(any(SentimentRule.class));
	}

	@Test
	@DisplayName("POST /rules rejects a base score outside [-1.0, 1.0]")
	void createRuleRejectsOutOfRangeBaseScore() throws Exception {
		String body = """
			{"eventType":"PushEvent","baseScore":2.0}
			""";

		mockMvc.perform(post("/rules").contentType(MediaType.APPLICATION_JSON).content(body))
		       .andExpect(status().isBadRequest());

		verify(sentimentRuleService, never()).save(any());
	}

	@Test
	@DisplayName("POST /rules rejects an invalid JSON rule definition")
	void createRuleRejectsInvalidJsonDefinition() throws Exception {
		String body = """
			{"eventType":"PushEvent","ruleDefinition":"not-json","baseScore":0.1}
			""";

		mockMvc.perform(post("/rules").contentType(MediaType.APPLICATION_JSON).content(body))
		       .andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /rules rejects an unregistered event type")
	void createRuleRejectsUnknownEventType() throws Exception {
		when(eventTypeService.findByName("Ghost")).thenReturn(null);
		String body = """
			{"eventType":"Ghost","ruleDefinition":"{}","baseScore":0.1}
			""";

		mockMvc.perform(post("/rules").contentType(MediaType.APPLICATION_JSON).content(body))
		       .andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("PUT /rules/{id} updates an existing rule")
	void updateRuleSuccess() throws Exception {
		when(sentimentRuleService.findById(1L)).thenReturn(rule(1L, "PushEvent", 0.3, 1));
		when(sentimentRuleService.save(any(SentimentRule.class))).thenAnswer(inv -> inv.getArgument(0));

		String body = """
			{"eventType":"PushEvent","ruleDefinition":"{\\"keywords\\":[]}","baseScore":-0.2,"explanation":"upd"}
			""";

		mockMvc.perform(put("/rules/1").contentType(MediaType.APPLICATION_JSON).content(body))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.baseScore").value(-0.2));
	}

	@Test
	@DisplayName("PUT /rules/{id} returns 404 for a missing rule")
	void updateRuleNotFound() throws Exception {
		when(sentimentRuleService.findById(77L)).thenReturn(null);
		String body = """
			{"eventType":"PushEvent","baseScore":0.1}
			""";

		mockMvc.perform(put("/rules/77").contentType(MediaType.APPLICATION_JSON).content(body))
		       .andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("DELETE /rules/{id} deletes and clears hasRule when no versions remain")
	void deleteRuleClearsHasRule() throws Exception {
		when(sentimentRuleService.findById(1L)).thenReturn(rule(1L, "PushEvent", 0.3, 1));
		when(sentimentRuleService.findByEventType("PushEvent")).thenReturn(List.of());
		EventType eventType = EventType.builder().name("PushEvent").hasRule(true).build();
		when(eventTypeService.findByName("PushEvent")).thenReturn(eventType);

		mockMvc.perform(delete("/rules/1"))
		       .andExpect(status().isNoContent());

		verify(sentimentRuleService).deleteById(1L);
		verify(eventTypeService).save(argThat(et -> !et.isHasRule()));
	}

	@Test
	@DisplayName("POST /rules/regenerate/{eventType} produces a new version via AI")
	void regenerateRuleSuccess() throws Exception {
		EventType eventType = EventType.builder().name("PushEvent").samplePayloadSchema("{}").build();
		when(eventTypeService.findByName("PushEvent")).thenReturn(eventType);
		when(ruleGenerationService.generateRule(eq("PushEvent"), anyString(), eq(true)))
			.thenReturn(rule(9L, "PushEvent", 0.7, 3));

		mockMvc.perform(post("/rules/regenerate/PushEvent"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.version").value(3));
	}

	@Test
	@DisplayName("POST /rules/regenerate/{eventType} returns 503 when AI generation fails")
	void regenerateRuleFailsWithServiceUnavailable() throws Exception {
		EventType eventType = EventType.builder().name("PushEvent").samplePayloadSchema("{}").build();
		when(eventTypeService.findByName("PushEvent")).thenReturn(eventType);
		when(ruleGenerationService.generateRule(eq("PushEvent"), anyString(), eq(true))).thenReturn(null);

		mockMvc.perform(post("/rules/regenerate/PushEvent"))
		       .andExpect(status().isServiceUnavailable());
	}

	@Test
	@DisplayName("POST /rules/reevaluate/{eventType} re-evaluates PENDING events retroactively")
	void reEvaluatePendingEvents() throws Exception {
		SentimentRule rule = rule(1L, "PushEvent", 0.3, 1);
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(rule);

		ProcessedEvent pending = ProcessedEvent.builder()
		                                       .eventId(50L)
		                                       .eventType("PushEvent")
		                                       .eventTimestamp(1L)
		                                       .sentimentScore(BigDecimal.ZERO)
		                                       .confidence(BigDecimal.ZERO)
		                                       .evaluationStatus(EvaluationStatus.PENDING)
		                                       .build();
		when(processedEventService.findPendingByEventType("PushEvent")).thenReturn(List.of(pending));
		RawEvent raw = RawEvent.builder().eventType("PushEvent").timestamp(1L).payload("{}").build();
		raw.setId(50L);
		when(rawEventService.findById(50L)).thenReturn(raw);
		when(sentimentEvaluationService.evaluate(any(RawEvent.class), eq(rule)))
			.thenReturn(new SentimentResult(0.4, 1L, 0.5));

		mockMvc.perform(post("/rules/reevaluate/PushEvent"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(org.hamcrest.Matchers.containsString("Re-evaluated 1")));

		verify(processedEventService).save(argThat(e -> e.getEvaluationStatus() == EvaluationStatus.COMPLETED));
	}

	@Test
	@DisplayName("POST /rules/reevaluate/{eventType} returns 400 when no rule exists")
	void reEvaluateWithoutRule() throws Exception {
		when(sentimentRuleService.findTopByEventTypeOrderByVersionDesc("PushEvent")).thenReturn(null);

		mockMvc.perform(post("/rules/reevaluate/PushEvent"))
		       .andExpect(status().isBadRequest());
	}
}
