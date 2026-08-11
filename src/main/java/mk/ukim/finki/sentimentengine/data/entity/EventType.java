package mk.ukim.finki.sentimentengine.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

/**
 * JPA entity representing a registered event type.
 *
 * @author kristina
 */
@Entity
@Table(name = "event_types")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventType extends GenericEntity {

	@Column(name = "name", nullable = false, unique = true, length = 128)
	private String name;

	@Column(name = "first_seen_at", nullable = false)
	private Long firstSeenAt;

	@Column(name = "last_seen_at", nullable = false)
	private Long lastSeenAt;

	@Column(name = "occurrence_count", nullable = false)
	private long occurrenceCount;

	@Column(name = "sample_payload_schema", columnDefinition = "JSON")
	private String samplePayloadSchema;

	@Column(name = "has_rule", nullable = false)
	private boolean hasRule;
}