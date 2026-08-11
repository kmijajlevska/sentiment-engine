package mk.ukim.finki.sentimentengine.api;

import mk.ukim.finki.sentimentengine.job.RuleGenerationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author kristina
 */
@RestController
@RequestMapping("/jobs")
public class JobsController {

	private static final Logger logger = LoggerFactory.getLogger(JobsController.class);
	private final RuleGenerationJob ruleGenerationJob;

	public JobsController(RuleGenerationJob ruleGenerationJob) {
		this.ruleGenerationJob = ruleGenerationJob;
	}

	@PostMapping("/rule-gen")
	public void generateMissingRules() {
		logger.info("[API][JOB] Manually invoking the Rule Generation job..");
		ruleGenerationJob.generateMissingRules();
	}


}
