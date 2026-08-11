package mk.ukim.finki.sentimentengine.data.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

/**
 * JPA entity representing a processed event with sentiment scoring
 * and pre-computed time buckets for efficient aggregation queries.
 *
 * @author kristina
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent extends GenericEntity {

	@Column(name = "event_id", nullable = false)
	private Long eventId;

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@Column(name = "event_timestamp", nullable = false)
	private long eventTimestamp;

	@Column(name = "sentiment_score", nullable = false)
	private double sentimentScore;

	@Column(name = "confidence", nullable = false)
	private double confidence;

	@Column(name = "applied_rule_id")
	private Long appliedRuleId;

	@Column(name = "minute_bucket", nullable = false)
	private long minuteBucket;

	@Column(name = "hour_bucket", nullable = false)
	private long hourBucket;

	@Column(name = "day_bucket", nullable = false)
	@Temporal(TemporalType.DATE)
	private Date dayBucket;

	@Column(name = "week_bucket", nullable = false)
	@Temporal(TemporalType.DATE)
	private Date weekBucket;

	@Column(name = "month_bucket", nullable = false)
	@Temporal(TemporalType.DATE)
	private Date monthBucket;

}