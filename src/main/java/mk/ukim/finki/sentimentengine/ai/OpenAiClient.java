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

import static org.apache.logging.log4j.util.StringBuilders.escapeJson;

/**
 * @author kristina
 */
@Component
@ConditionalOnProperty(name = "genai.provider", havingValue = "openai")
public class OpenAiClient implements GenAiClient {

	@Value("${genai.openai.api-key}")
	private String apiKey;
	@Value("${genai.openai.model}")
	private String model;
	@Value("${genai.openai.url}")
	private String url;
	@Value("${genai.openai.temperature}")
	private double temperature;

	private RestClient restClient;
	private final ObjectMapper objectMapper;

	public OpenAiClient(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	void init() {
		this.restClient = RestClient.builder()
		                            .baseUrl(url)
		                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
		                            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
		                            .build();
	}

	// Setup:
// Go to platform.openai.com, sign up
// Add $5 credit (Settings → Billing)
// Go to API Keys → Create new secret key
// Save the key in application.properties


	@Override
	public String generateCompletion(String prompt) throws GenAiException {
		try {
			Map<String, Object> requestBody = Map.of(
				"model", model,
				"messages", List.of(Map.of("role", "user", "content", prompt)),
				"temperature", temperature
			);

			String jsonBody = objectMapper.writeValueAsString(requestBody);

			String response = restClient.post()
			                            .body(jsonBody)
			                            .retrieve()
			                            .body(String.class);

			return extractOpenAiContent(response);

		} catch (GenAiException e) {
			throw e;
		} catch (Exception e) {
			throw new GenAiException("OpenAI call failed: " + e.getMessage(), e);
		}
	}

	private String extractOpenAiContent(String responseBody) throws GenAiException {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode choices = root.get("choices");
			if (choices == null || choices.isEmpty()) {
				throw new GenAiException("OpenAI response contains no choices");
			}
			JsonNode content = choices.get(0).get("message").get("content");
			if (content == null) {
				throw new GenAiException("OpenAI response has no content");
			}
			return content.asString();
		} catch (GenAiException e) {
			throw e;
		} catch (Exception e) {
			throw new GenAiException("Failed to parse OpenAI response: " + e.getMessage(), e);
		}
	}
}

