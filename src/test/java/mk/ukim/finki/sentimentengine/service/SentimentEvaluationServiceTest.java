package mk.ukim.finki.sentimentengine.service;

import mk.ukim.finki.sentimentengine.data.entity.RawEvent;
import mk.ukim.finki.sentimentengine.data.entity.SentimentResult;
import mk.ukim.finki.sentimentengine.data.entity.SentimentRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link SentimentEvaluationService}.
 *
 * <p>Maps to user scenario 4.2 (Rule Management) / 4.3 (Analytics): once a rule exists for an
 * event type, each event is scored by adjusting the rule's base impact according to the keywords
 * present in the event payload. When no usable rule exists the event cannot be scored and must be
 * flagged for later (PENDING) evaluation.
 */
class SentimentEvaluationServiceTest {

	private SentimentEvaluationService sentimentEvaluationService;

	@BeforeEach
	void setUp() {
		sentimentEvaluationService = new SentimentEvaluationService(new ObjectMapper());
	}

	private RawEvent event(String payload) {
		return RawEvent.builder()
		               .eventType("PushEvent")
		               .timestamp(1700000000000L)
		               .source("octocat/hello-world")
		               .payload(payload)
		               .build();
	}

	private SentimentRule rule(double baseScore, String ruleDefinition) {
		SentimentRule rule = SentimentRule.builder()
		                                  .eventType("PushEvent")
		                                  .ruleType("EVENT")
		                                  .baseScore(baseScore)
		                                  .ruleDefinition(ruleDefinition)
		                                  .version(1)
		                                  .build();
		rule.setId(42L);
		return rule;
	}

	@Test
	@DisplayName("Returns null when no rule is available so the event stays PENDING")
	void returnsNullWhenNoRule() {
		SentimentResult result = sentimentEvaluationService.evaluate(event("{\"msg\":\"anything\"}"), null);

		assertThat(result).isNull();
	}

	@Test
	@DisplayName("Uses the plain base score when the rule has no keywords")
	void usesBaseScoreWhenNoKeywords() {
		SentimentResult result = sentimentEvaluationService.evaluate(
			event("{\"msg\":\"neutral text\"}"),
			rule(0.3, "{\"keywords\":[]}"));

		assertThat(result).isNotNull();
		assertThat(result.score()).isCloseTo(0.3, within(1e-9));
		assertThat(result.ruleId()).isEqualTo(42L);
		assertThat(result.confidence()).isEqualTo(0.0);
	}

	@Test
	@DisplayName("Positive keyword hits raise the score by 0.1 each")
	void positiveKeywordsIncreaseScore() {
		SentimentResult result = sentimentEvaluationService.evaluate(
			event("{\"action\":\"merged and fixed\"}"),
			rule(0.2, "{\"keywords\":[\"+merged\",\"+fixed\"]}"));

		// base 0.2 + 2 positive hits * 0.1 = 0.4
		assertThat(result.score()).isCloseTo(0.4, within(1e-9));
		// both keywords matched -> confidence = 2/2
		assertThat(result.confidence()).isCloseTo(1.0, within(1e-9));
	}

	@Test
	@DisplayName("Negative keyword hits lower the score by 0.1 each")
	void negativeKeywordsDecreaseScore() {
		SentimentResult result = sentimentEvaluationService.evaluate(
			event("{\"action\":\"build failed with error\"}"),
			rule(0.0, "{\"keywords\":[\"-failed\",\"-error\"]}"));

		// base 0.0 - 2 negative hits * 0.1 = -0.2
		assertThat(result.score()).isCloseTo(-0.2, within(1e-9));
	}

	@Test
	@DisplayName("Keyword matching is case-insensitive")
	void keywordMatchingIsCaseInsensitive() {
		SentimentResult result = sentimentEvaluationService.evaluate(
			event("{\"action\":\"MERGED\"}"),
			rule(0.0, "{\"keywords\":[\"+merged\"]}"));

		assertThat(result.score()).isCloseTo(0.1, within(1e-9));
	}

	@Test
	@DisplayName("Score is clamped to the [-1.0, 1.0] range")
	void scoreIsClampedToUpperBound() {
		SentimentResult result = sentimentEvaluationService.evaluate(
			event("{\"action\":\"good great nice ok fine super\"}"),
			rule(0.9, "{\"keywords\":[\"+good\",\"+great\",\"+nice\",\"+ok\",\"+fine\",\"+super\"]}"));

		// 0.9 + 6*0.1 = 1.5 -> clamped to 1.0
		assertThat(result.score()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("Score is clamped to the lower bound of -1.0")
	void scoreIsClampedToLowerBound() {
		SentimentResult result = sentimentEvaluationService.evaluate(
			event("{\"action\":\"bad worse terrible awful nasty horrible\"}"),
			rule(-0.9, "{\"keywords\":[\"-bad\",\"-worse\",\"-terrible\",\"-awful\",\"-nasty\",\"-horrible\"]}"));

		// -0.9 - 6*0.1 = -1.5 -> clamped to -1.0
		assertThat(result.score()).isEqualTo(-1.0);
	}

	@Test
	@DisplayName("Falls back to the base score when the rule definition is null")
	void fallsBackToBaseScoreWhenDefinitionNull() {
		SentimentResult result = sentimentEvaluationService.evaluate(event("{\"msg\":\"x\"}"), rule(0.5, null));

		assertThat(result.score()).isCloseTo(0.5, within(1e-9));
		assertThat(result.confidence()).isEqualTo(0.0);
	}

	@Test
	@DisplayName("Falls back to the base score when the rule definition is not valid JSON")
	void fallsBackToBaseScoreWhenDefinitionMalformed() {
		SentimentResult result = sentimentEvaluationService.evaluate(event("{\"msg\":\"x\"}"), rule(-0.4, "not-json"));

		assertThat(result.score()).isCloseTo(-0.4, within(1e-9));
	}

	@Test
	@DisplayName("Falls back to the base score when the event payload is empty")
	void fallsBackToBaseScoreWhenPayloadBlank() {
		SentimentResult result = sentimentEvaluationService.evaluate(event("  "), rule(0.25, "{\"keywords\":[\"+merged\"]}"));

		assertThat(result.score()).isCloseTo(0.25, within(1e-9));
	}

	@Test
	@DisplayName("Only matched keywords count toward confidence")
	void confidenceReflectsFractionOfMatchedKeywords() {
		SentimentResult result = sentimentEvaluationService.evaluate(
			event("{\"action\":\"merged\"}"),
			rule(0.0, "{\"keywords\":[\"+merged\",\"-failed\",\"-error\",\"+released\"]}"));

		// 1 of 4 keywords matched
		assertThat(result.confidence()).isCloseTo(0.25, within(1e-9));
		assertThat(result.score()).isCloseTo(0.1, within(1e-9));
	}
}
