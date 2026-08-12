package mk.ukim.finki.sentimentengine.util;

/**
 * @author kristina
 */
public final class RuleGenerationUtils {

 private RuleGenerationUtils() {
 }

	public static final String PROMPT_TEMPLATE = """
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
	public static final String FALLBACK_JSON = """
		{"baseScore":0.0,"keywords":[],"explanation":"Fallback - AI unavailable"}""";

}
