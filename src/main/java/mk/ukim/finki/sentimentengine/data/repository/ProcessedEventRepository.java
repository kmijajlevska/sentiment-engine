package mk.ukim.finki.sentimentengine.data.repository;

import mk.ukim.finki.sentimentengine.data.entity.EvaluationStatus;
import mk.ukim.finki.sentimentengine.data.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * @author kristina
 */
@Repository
public interface ProcessedEventRepository extends GenericRepository<ProcessedEvent> {

	// ─── Time-series aggregation queries ───────────────────────────────────────

	@Query(value = "SELECT minute_bucket AS bucket_start, COUNT(*) AS event_count, " +
		"AVG(sentiment_score) AS avg_sentiment, MIN(sentiment_score) AS min_sentiment, " +
		"MAX(sentiment_score) AS max_sentiment, " +
		"SUM(CASE WHEN event_type LIKE 'system.absence.%' THEN 1 ELSE 0 END) AS absence_count " +
		"FROM processed_events WHERE (:eventType IS NULL OR event_type = :eventType) " +
		"AND minute_bucket >= :from AND minute_bucket < :to " +
		"AND evaluation_status = 'COMPLETED' " +
		"GROUP BY minute_bucket ORDER BY minute_bucket", nativeQuery = true)
	List<Object[]> aggregateByMinute(@Param("eventType") String eventType,
	                                 @Param("from") long from,
	                                 @Param("to") long to);

	@Query(value = "SELECT hour_bucket AS bucket_start, COUNT(*) AS event_count, " +
		"AVG(sentiment_score) AS avg_sentiment, MIN(sentiment_score) AS min_sentiment, " +
		"MAX(sentiment_score) AS max_sentiment, " +
		"SUM(CASE WHEN event_type LIKE 'system.absence.%' THEN 1 ELSE 0 END) AS absence_count " +
		"FROM processed_events WHERE (:eventType IS NULL OR event_type = :eventType) " +
		"AND hour_bucket >= :from AND hour_bucket < :to " +
		"AND evaluation_status = 'COMPLETED' " +
		"GROUP BY hour_bucket ORDER BY hour_bucket", nativeQuery = true)
	List<Object[]> aggregateByHour(@Param("eventType") String eventType,
	                               @Param("from") long from,
	                               @Param("to") long to);

	@Query(value = "SELECT day_bucket AS bucket_start, COUNT(*) AS event_count, " +
		"AVG(sentiment_score) AS avg_sentiment, MIN(sentiment_score) AS min_sentiment, " +
		"MAX(sentiment_score) AS max_sentiment, " +
		"SUM(CASE WHEN event_type LIKE 'system.absence.%' THEN 1 ELSE 0 END) AS absence_count " +
		"FROM processed_events WHERE (:eventType IS NULL OR event_type = :eventType) " +
		"AND day_bucket >= :from AND day_bucket < :to " +
		"AND evaluation_status = 'COMPLETED' " +
		"GROUP BY day_bucket ORDER BY day_bucket", nativeQuery = true)
	List<Object[]> aggregateByDay(@Param("eventType") String eventType,
	                              @Param("from") Date from,
	                              @Param("to") Date to);

	@Query(value = "SELECT week_bucket AS bucket_start, COUNT(*) AS event_count, " +
		"AVG(sentiment_score) AS avg_sentiment, MIN(sentiment_score) AS min_sentiment, " +
		"MAX(sentiment_score) AS max_sentiment, " +
		"SUM(CASE WHEN event_type LIKE 'system.absence.%' THEN 1 ELSE 0 END) AS absence_count " +
		"FROM processed_events WHERE (:eventType IS NULL OR event_type = :eventType) " +
		"AND week_bucket >= :from AND week_bucket < :to " +
		"AND evaluation_status = 'COMPLETED' " +
		"GROUP BY week_bucket ORDER BY week_bucket", nativeQuery = true)
	List<Object[]> aggregateByWeek(@Param("eventType") String eventType,
	                               @Param("from") Date from,
	                               @Param("to") Date to);

	@Query(value = "SELECT month_bucket AS bucket_start, COUNT(*) AS event_count, " +
		"AVG(sentiment_score) AS avg_sentiment, MIN(sentiment_score) AS min_sentiment, " +
		"MAX(sentiment_score) AS max_sentiment, " +
		"SUM(CASE WHEN event_type LIKE 'system.absence.%' THEN 1 ELSE 0 END) AS absence_count " +
		"FROM processed_events WHERE (:eventType IS NULL OR event_type = :eventType) " +
		"AND month_bucket >= :from AND month_bucket < :to " +
		"AND evaluation_status = 'COMPLETED' " +
		"GROUP BY month_bucket ORDER BY month_bucket", nativeQuery = true)
	List<Object[]> aggregateByMonth(@Param("eventType") String eventType,
	                                @Param("from") Date from,
	                                @Param("to") Date to);

	// ─── Detail view queries ────────────────────────────────────────────────────

	List<ProcessedEvent> findByEventTypeAndMinuteBucketAndEvaluationStatus(String eventType, long minuteBucket, EvaluationStatus evaluationStatus);

	List<ProcessedEvent> findByEventTypeAndHourBucketAndEvaluationStatus(String eventType, long hourBucket, EvaluationStatus evaluationStatus);

	List<ProcessedEvent> findByEventTypeAndDayBucketAndEvaluationStatus(String eventType, Date dayBucket, EvaluationStatus evaluationStatus);

	List<ProcessedEvent> findByEventTypeAndWeekBucketAndEvaluationStatus(String eventType, Date weekBucket, EvaluationStatus evaluationStatus);

	List<ProcessedEvent> findByEventTypeAndMonthBucketAndEvaluationStatus(String eventType, Date monthBucket, EvaluationStatus evaluationStatus);

	// ─── Re-evaluation queries ──────────────────────────────────────────────────

	List<ProcessedEvent> findByEvaluationStatusAndEventType(EvaluationStatus evaluationStatus, String eventType);

	// ─── Rule management queries ────────────────────────────────────────────────

	@Query("SELECT pe.eventType, COUNT(pe) FROM ProcessedEvent pe " +
		"WHERE pe.evaluationStatus = mk.ukim.finki.sentimentengine.data.entity.EvaluationStatus.PENDING GROUP BY pe.eventType")
	List<Object[]> countPendingByEventType();

	@Query("SELECT COUNT(pe) FROM ProcessedEvent pe " +
		"WHERE pe.evaluationStatus = mk.ukim.finki.sentimentengine.data.entity.EvaluationStatus.PENDING AND pe.eventType = :eventType")
	long countPendingByEventType(@Param("eventType") String eventType);

	@Query("SELECT pe.appliedRuleId, COUNT(pe) FROM ProcessedEvent pe " +
		"WHERE pe.appliedRuleId IS NOT NULL GROUP BY pe.appliedRuleId")
	List<Object[]> countAssignedByRuleIdGrouped();
}
