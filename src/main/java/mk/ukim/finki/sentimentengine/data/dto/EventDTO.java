package mk.ukim.finki.sentimentengine.data.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * DTO representation of an event extracted from external sources.
 * Extended with id and metrics for better traceability
 *
 * @author kristina
 */
@Data
public class EventDTO implements Serializable {
	@Serial
	private static final long serialVersionUID = 0L;
	private UUID id;
	private String eventType;
	private Long eventTimestamp;
	private String source;
	private String payload;
	private MetricsDTO metrics;

	public EventDTO() {
	}

	public EventDTO(UUID id, String eventType, Long eventTimestamp, String source,
	                String payload, MetricsDTO metrics) {
		this.id = id;
		this.eventType = eventType;
		this.eventTimestamp = eventTimestamp;
		this.source = source;
		this.payload = payload;
		this.metrics = metrics;
	}


	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (EventDTO) obj;
		return Objects.equals(this.id, that.id) &&
			Objects.equals(this.eventType, that.eventType) &&
			Objects.equals(this.eventTimestamp, that.eventTimestamp) &&
			Objects.equals(this.source, that.source) &&
			Objects.equals(this.payload, that.payload) &&
			Objects.equals(this.metrics, that.metrics);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, eventType, eventTimestamp, source, payload, metrics);
	}

	@Override
	public String toString() {
		return "EventDTO[" +
			"id=" + id + ", " +
			"eventType=" + eventType + ", " +
			"eventTimestamp=" + eventTimestamp + ", " +
			"source=" + source + ", " +
			"payload=" + payload + ", " +
			"metrics=" + metrics + ']';
	}
}
