package mk.ukim.finki.sentimentengine.data.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity for persisted analytics events.
 *
 * @author kristina
 */
@Entity
@Table(name = "raw_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawEvent extends GenericEntity {

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@Column(name = "timestamp", nullable = false)
	private Long timestamp;

	@Column(name = "source", length = 256)
	private String source;

	@Column(name = "payload", columnDefinition = "JSON")
	private String payload; // if necessary store as compressed bytes

}
