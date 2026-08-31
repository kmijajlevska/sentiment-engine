package mk.ukim.finki.sentimentengine.api;

import mk.ukim.finki.sentimentengine.job.AbsenceDetectionJob;
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
	private final AbsenceDetectionJob absenceDetectionJob;

	public JobsController(RuleGenerationJob ruleGenerationJob,
	                      @Autowired(required = false) ReEvaluationJob reEvaluationJob,
	                      @Autowired(required = false) AbsenceDetectionJob absenceDetectionJob) {
		this.ruleGenerationJob = ruleGenerationJob;
		this.reEvaluationJob = reEvaluationJob;
		this.absenceDetectionJob = absenceDetectionJob;
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

	@PostMapping("/absence")
	public void checkForAbsences() {
		if (absenceDetectionJob == null) {
			logger.warn("[API][JOB] Absence Detection job is not enabled");
			return;
		}
		logger.info("[API][JOB] Manually invoking the Absence Detection job..");
		absenceDetectionJob.checkForAbsences();
	}

}
