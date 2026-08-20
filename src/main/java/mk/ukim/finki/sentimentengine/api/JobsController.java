package mk.ukim.finki.sentimentengine.api;

import mk.ukim.finki.sentimentengine.job.ReEvaluationJob;
import mk.ukim.finki.sentimentengine.job.RuleGenerationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
	private final ReEvaluationJob reEvaluationJob;

	public JobsController(RuleGenerationJob ruleGenerationJob,
	                      @Autowired(required = false) ReEvaluationJob reEvaluationJob) {
		this.ruleGenerationJob = ruleGenerationJob;
		this.reEvaluationJob = reEvaluationJob;
	}

	@PostMapping("/rule-gen")
	public void generateMissingRules() {
		logger.info("[API][JOB] Manually invoking the Rule Generation job..");
		ruleGenerationJob.generateMissingRules();
	}

	@PostMapping("/reevaluate")
	public void reEvaluatePendingEvents() {
		if (reEvaluationJob == null) {
			logger.warn("[API][JOB] Re-Evaluation job is not enabled");
			return;
		}
		logger.info("[API][JOB] Manually invoking the Re-Evaluation job..");
		reEvaluationJob.reEvaluatePendingEvents();
	}

}
