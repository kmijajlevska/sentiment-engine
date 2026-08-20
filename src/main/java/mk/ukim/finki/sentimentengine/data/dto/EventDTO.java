package mk.ukim.finki.sentimentengine.data.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * DTO representation of an event extracted from external sources.
 * Extended with id and metrics for better traceability
 *
 * @author kristina
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventDTO implements Serializable {
	@Serial
	private static final long serialVersionUID = 0L;
	private UUID id;
	private String eventType;
	private Long eventTimestamp;
	private String source;
	private String payload;
	private MetricsDTO metrics;

}
