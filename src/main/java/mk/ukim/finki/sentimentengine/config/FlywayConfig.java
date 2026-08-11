package mk.ukim.finki.sentimentengine.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author kristina
 */
@Configuration
public class FlywayConfig {

	@Value("${flyway.clean:false}")
	private boolean cleanOnStartup;

	@Bean
	public FlywayMigrationStrategy cleanMigrateStrategy() {
		return flyway -> {
			if (cleanOnStartup) {
				flyway.clean();
			}
			flyway.migrate();
		};
	}
}
