package mk.ukim.finki.sentimentengine.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

/**
 * JPA entity representing an AI-generated sentiment rule for a specific event type.
 *
 * @author kristina
 */
@Entity
@Table(name = "sentiment_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentRule extends GenericEntity {

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@Column(name = "rule_type", nullable = false, length = 64)
	private String ruleType;

	@Column(name = "rule_definition", columnDefinition = "JSON")
	private String ruleDefinition;

	@Column(name = "base_score", nullable = false)
	private double baseScore;

	@Column(name = "explanation", columnDefinition = "TEXT")
	private String explanation;

	@Column(name = "version", nullable = false)
	private int version;
}
