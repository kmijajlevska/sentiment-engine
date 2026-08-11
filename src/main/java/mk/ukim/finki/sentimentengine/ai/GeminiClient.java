package mk.ukim.finki.sentimentengine.ai;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * @author kristina
 */
@Component
@ConditionalOnProperty(name = "genai.provider", havingValue = "gemini")
public class GeminiClient implements GenAiClient {

	private RestClient restClient;
	private ObjectMapper objectMapper;

	@Value("${genai.gemini.api-key}")
	private String apiKey;
	@Value("${genai.gemini.model}")
	private String model;
	@Value("${genai.gemini.url}")
	private String url;


	//	Go to aistudio.google.com/apikey
//	Click "Create API Key" — it's free, no credit card needed
//	Save the key in your application.properties
//
	@PostConstruct
	public void init() {
		this.objectMapper = new ObjectMapper();
		this.restClient = RestClient.builder()
		                            .baseUrl(url)
		                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
		                            .build();
	}

	@Override
	public String generateCompletion(String prompt) throws GenAiException {
		try {
			Map<String, Object> requestBody = Map.of(
				"contents", List.of(
					Map.of("parts", List.of(Map.of("text", prompt)))
				));

			String jsonBody = objectMapper.writeValueAsString(requestBody);

			String response = restClient.post()
			                            .uri("/{model}:generateContent?key={key}", model, apiKey)
			                            .body(jsonBody)
			                            .retrieve()
			                            .body(String.class);

			return extractGeminiContent(response); //todo add stripping here

		} catch (GenAiException e) {
			throw e;
		} catch (Exception e) {
			throw new GenAiException("Gemini call failed: " + e.getMessage(), e);
		}
	}

	private String extractGeminiContent(String responseBody) throws GenAiException {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode candidates = root.get("candidates");
			if (candidates == null || candidates.isEmpty()) {
				throw new GenAiException("Gemini response contains no candidates");
			}
			JsonNode parts = candidates.get(0).get("content").get("parts");
			if (parts == null || parts.isEmpty()) {
				throw new GenAiException("Gemini response has no parts");
			}
			return parts.get(0).get("text").asString();
		} catch (GenAiException e) {
			throw e;
		} catch (Exception e) {
			throw new GenAiException("Failed to parse Gemini response: " + e.getMessage(), e);
		}
	}
}