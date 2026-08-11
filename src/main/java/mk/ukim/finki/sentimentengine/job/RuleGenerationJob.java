package mk.ukim.finki.sentimentengine.job;

import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.data.service.EventTypeService;
import mk.ukim.finki.sentimentengine.service.RuleGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled job for generating rules for new events
 *
 * @author kristina
 */
@ConditionalOnProperty(name = "rulegen.job.enabled", havingValue = "true")
@Service
public class RuleGenerationJob {

	private static final Logger logger = LoggerFactory.getLogger(RuleGenerationJob.class);
	private final EventTypeService eventTypeService;
	private final RuleGenerationService ruleGenerationService;

	public RuleGenerationJob(EventTypeService eventTypeService, RuleGenerationService ruleGenerationService) {
		this.eventTypeService = eventTypeService;
		this.ruleGenerationService = ruleGenerationService;
	}

	@Scheduled(fixedDelayString = "${rulegen.job.interval-ms:300000}", initialDelay = 60000L)
	public void generateMissingRules() {
		long start = System.currentTimeMillis();
		logger.info("[RULE-GEN][JOB] Running the Rule Generation scheduled job ..");
		List<EventType> typesWithoutRules = eventTypeService.findWithoutRules();

		for (EventType type : typesWithoutRules) {
			ruleGenerationService.generateRule(type.getName(), type.getSamplePayloadSchema());
		}

		logger.info("[RULE-GEN][JOB] Finished job for {} events without rules in {} ms",
			typesWithoutRules.size(), System.currentTimeMillis() - start);

	}
}
