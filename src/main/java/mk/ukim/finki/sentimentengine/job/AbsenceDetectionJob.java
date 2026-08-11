package mk.ukim.finki.sentimentengine.job;

import mk.ukim.finki.sentimentengine.service.AbsenceDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled job for detecting absence of events
 *
 * @author kristina
 */
@ConditionalOnProperty(name = "absence.check.job.enabled", havingValue = "true")
@Service
public class AbsenceDetectionJob {

	private static final Logger logger = LoggerFactory.getLogger(AbsenceDetectionJob.class);
	private final AbsenceDetectionService absenceDetectionService;

	public AbsenceDetectionJob(AbsenceDetectionService absenceDetectionService) {
		this.absenceDetectionService = absenceDetectionService;
	}

	@Scheduled(fixedDelayString = "${absence.check-interval-ms:60000}", initialDelay = 120000L)
	public void checkForAbsences() {
		logger.info("[ABSENCE-DETECTION][JOB] Running the Absence Detection job .. ");
		long now = System.currentTimeMillis();

		absenceDetectionService.checkGlobalAbsence(now);
		absenceDetectionService.checkPerTypeAbsence(now);
	}
}
