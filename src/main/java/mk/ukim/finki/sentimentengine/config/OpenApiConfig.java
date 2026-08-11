package mk.ukim.finki.sentimentengine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author kristina
 */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI sentimentEngineOpenAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("SentimentEngine API")
				.description("API for sentiment analysis of events")
				.version("0.0.1-SNAPSHOT"));
	}
}
