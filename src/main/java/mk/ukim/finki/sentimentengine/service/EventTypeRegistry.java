package mk.ukim.finki.sentimentengine.service;


import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.data.service.EventTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kristina
 */
@Service
@RequiredArgsConstructor
public class EventTypeRegistry {

	private static final Logger logger = LoggerFactory.getLogger(EventTypeRegistry.class);

	private final EventTypeService eventTypeService;
	private final ConcurrentHashMap<String, Boolean> knownTypes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> lastSeenPerType = new ConcurrentHashMap<>();

	@PostConstruct
	void init() {
//		eventTypeService.findAll().forEach(et -> knownTypes.put(et.getName(), Boolean.TRUE));

		eventTypeService.findAll().forEach(et -> {
			knownTypes.put(et.getName(), Boolean.TRUE);
			if (et.getLastSeenAt() > 0) {
				lastSeenPerType.put(et.getName(), et.getLastSeenAt());
			}
		});
		logger.info("[TYPE-REGISTRY] EventTypeRegistry initialized with known types: {}", knownTypes.keySet());
	}

	public boolean isKnownType(String eventType) {
		if (knownTypes.containsKey(eventType)) {
			logger.debug("[TYPE-REGISTRY] Event type: {} is already known and registered", eventType);
			return true;
		}
		// Cache miss — check DB in case another instance registered it
		boolean exists = (eventTypeService.findByName(eventType) != null);
		if (exists) {
			logger.debug("[TYPE-REGISTRY] Event type: {} is known, adding to cache", eventType);
			knownTypes.put(eventType, Boolean.TRUE);
		}
		return exists;
	}

	public void register(String eventType, String samplePayload, long timestamp) {
		EventType entity = EventType.builder()
		                            .name(eventType)
		                            .firstSeenAt(timestamp)
		                            .lastSeenAt(timestamp)
		                            .occurrenceCount(1L)
		                            .samplePayloadSchema(samplePayload)
		                            .hasRule(false)
		                            .build();

		try {
			eventTypeService.save(entity);
			knownTypes.put(eventType, Boolean.TRUE);
			logger.info("[TYPE-REGISTRY] Registered new event type: {}", eventType);
		} catch (DataIntegrityViolationException e) {
			// Concurrent registration of the same type — load existing and update cache
			logger.debug("[TYPE-REGISTRY] Event type: {} already registered concurrently", eventType);
			knownTypes.put(eventType, Boolean.TRUE);
		}
	}

	// this method is invoked after each event, should be optimized with cache writes and periodical flushes to db
	public void recordOccurrence(String eventType, long timestamp) {
		EventType eventTypeEntity = eventTypeService.findByName(eventType);
		if (eventTypeEntity != null) {
			eventTypeEntity.setOccurrenceCount(eventTypeEntity.getOccurrenceCount() + 1);
			eventTypeEntity.setLastSeenAt(timestamp);
			eventTypeService.save(eventTypeEntity);
		}
	}

	public void updateLastSeenPerType(String eventType, long timestamp) {
		lastSeenPerType.merge(eventType, timestamp, Math::max);
	}

	public long getLastSeenAt(String eventType) {
		return lastSeenPerType.getOrDefault(eventType, 0L);
	}

	public List<EventType> getAllEventTypes() {
		return eventTypeService.findAll();
	}

}
