package mk.ukim.finki.sentimentengine.util;

import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.data.dto.MetricsDTO;

import java.util.UUID;

/**
 * @author kristina
 */
public final class DataSourceTransformer {

	private DataSourceTransformer() {
	}

	public static EventDTO generateEvent(String eventType, Long timestamp, String source, String payload) {
		MetricsDTO metricsDTO = new MetricsDTO();
		metricsDTO.setImportedAt(System.currentTimeMillis());
		UUID eventId = UUID.randomUUID();
		return new EventDTO(eventId, eventType, timestamp, source, payload, metricsDTO);
	}
}
