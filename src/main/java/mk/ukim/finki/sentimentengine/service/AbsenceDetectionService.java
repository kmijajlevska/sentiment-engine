package mk.ukim.finki.sentimentengine.service;

import jakarta.annotation.PostConstruct;
import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.data.dto.MetricsDTO;
import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.data.repository.RawEventRepository;
import mk.ukim.finki.sentimentengine.data.service.RawEventService;
import mk.ukim.finki.sentimentengine.messaging.InternalBufferProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author kristina
 */
@Service
public class AbsenceDetectionService {

	public static final String ABSENCE_EVENT_TYPE = "system.absence.";
	public static final String GLOBAL_ABSENCE_EVENT_TYPE = ABSENCE_EVENT_TYPE + "global";
	public static final String TYPE_ABSENCE_EVENT_TYPE = ABSENCE_EVENT_TYPE + "type";
	private static final Logger log = LoggerFactory.getLogger(AbsenceDetectionService.class);
	private final EventTypeRegistry eventTypeRegistry;
	private final RawEventRepository rawEventRepository;
	private final InternalBufferProducer bufferProducer;
	private final RawEventService rawEventService;
	private final ObjectMapper objectMapper;

	private final Set<String> perTypeAbsenceFired = ConcurrentHashMap.newKeySet();
	private final AtomicLong lastTimestampReceived = new AtomicLong(0);
	private volatile boolean globalAbsenceFired = false;
	@Value("${absence.check.on.arrival.enabled:true}")
	private boolean checkAbsenceOnArrivalEnabled;

	@Value("${absence.threshold-ms.global:300000}")
	private int absenceGlobalThresholdMs;

	@Value("${absence.threshold-ms.per-type:600000}")
	private int absencePerTypeThresholdMs;

	public AbsenceDetectionService(EventTypeRegistry eventTypeRegistry,
	                               RawEventRepository rawEventRepository,
	                               InternalBufferProducer bufferProducer, RawEventService rawEventService, ObjectMapper objectMapper) {
		this.eventTypeRegistry = eventTypeRegistry;
		this.rawEventRepository = rawEventRepository;
		this.bufferProducer = bufferProducer;
		this.rawEventService = rawEventService;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	public void init() {
		Long lastTimestamp = rawEventService.findLastTimestamp();
		lastTimestampReceived.set(lastTimestamp != null ? lastTimestamp : 0L);
		log.info("AbsenceDetectionService initialized: lastTimestampReceived={}", lastTimestampReceived.get());
	}


	public void updateLastReceivedTimestamp(long timestamp) {
		lastTimestampReceived.updateAndGet(current -> Math.max(current, timestamp));
	}


	public void checkGlobalAbsence(long now) {
		long lastTimestamp = lastTimestampReceived.get();

		if (lastTimestamp == 0) {
			log.debug("No events received yet, skipping global absence check");
			return;
		}

		long gap = now - lastTimestamp;
		if (gap > absenceGlobalThresholdMs && !globalAbsenceFired) {
			EventDTO absenceEventDto = this.createAbsenceEventDto(GLOBAL_ABSENCE_EVENT_TYPE, lastTimestamp, gap, now, false);
			bufferProducer.sendToBuffer(absenceEventDto);
			globalAbsenceFired = true;

			log.info("[ABSENCE-DETECTION][GLOBAL] Detected global absence, lastReceivedTimestamp:{}, gapDurationMs:{}", lastTimestamp, gap);
		} else {
			globalAbsenceFired = false; // reset flag
			log.info("[ABSENCE-DETECTION][GLOBAL] No absence detected");
		}
	}

	public void checkPerTypeAbsence(long now) {
		List<EventType> allTypes = eventTypeRegistry.getAllEventTypes();

		// fixme exclude absence events here
		for (EventType type : allTypes) {
			if (type.getName().contains(ABSENCE_EVENT_TYPE))
				continue;
			long lastSeenAt = type.getLastSeenAt();
			if (lastSeenAt == 0) {
				continue;
			}

			long gap = now - lastSeenAt;
			if (gap > absencePerTypeThresholdMs) {
				String typeName = type.getName();

				if (!perTypeAbsenceFired.contains(typeName)) {
					EventDTO absenceEventDto = this.createAbsenceEventDto(typeName, lastSeenAt, gap, now, false);
					bufferProducer.sendToBuffer(absenceEventDto);
					perTypeAbsenceFired.add(typeName);

					log.info("[ABSENCE-DETECTION][TYPE] Detected absence for event type:{}, lastSeenAt={}, gapDurationMs={}",
						typeName, lastSeenAt, gap);
				}
			} else {
				perTypeAbsenceFired.remove(type.getName());
				log.info("[ABSENCE-DETECTION][TYPE] No absence detected for event type: {}", type.getName());

			}
		}
	}


	public void checkForAbsenceOnArrival(Long eventTimestamp, String eventType) {
		if (!checkAbsenceOnArrivalEnabled) {
			return;
		}
		long lastTimestamp = lastTimestampReceived.get();

		if (lastTimestamp == 0) {
			return; // first event
		}

		long gap = eventTimestamp - lastTimestamp;

		// GLOBAL
		if (gap > absenceGlobalThresholdMs && !globalAbsenceFired) {
			EventDTO absenceEventDto = this.createAbsenceEventDto(GLOBAL_ABSENCE_EVENT_TYPE, lastTimestamp, gap,
				lastTimestamp + absenceGlobalThresholdMs, true);
			bufferProducer.sendToBuffer(absenceEventDto);
			globalAbsenceFired = true;

			log.info("[ABSENCE-DETECTION][GLOBAL] Detected global absence on arrival, lastReceivedTimestamp:{}, gapDurationMs:{}", lastTimestamp, gap);
		}

		// TYPE
		long perTypeLastSeen = eventTypeRegistry.getLastSeenAt(eventType);
		if (perTypeLastSeen > 0) {
			long perTypeGap = eventTimestamp - perTypeLastSeen;
			if (perTypeGap > absencePerTypeThresholdMs && !perTypeAbsenceFired.contains(eventType)) {

				String messageType = TYPE_ABSENCE_EVENT_TYPE + "." + eventType;
				EventDTO typeAbsenceEventDto = this.createAbsenceEventDto(messageType, perTypeLastSeen, perTypeGap,
					perTypeLastSeen + absencePerTypeThresholdMs, true);
				bufferProducer.sendToBuffer(typeAbsenceEventDto);
				perTypeAbsenceFired.add(eventType);

				log.info("[ABSENCE-DETECTION][TYPE] Detected absence on arrival for event type:{}, lastSeenAt={}, gapDurationMs={}",
					eventType, perTypeLastSeen, gap);
			}
		}
	}

	private EventDTO createAbsenceEventDto(String eventType, long lastTimestamp, long gap, long eventTimestamp, boolean onArrival) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("lastSeenAt", String.valueOf(lastTimestamp));
		payload.put("gapDurationMs", gap);
		payload.put("detectedBy", onArrival ? "on-arrival" : "absence-detection-service");
		if (!eventType.startsWith(ABSENCE_EVENT_TYPE)) {
			payload.put("eventType", eventType);
		}

		MetricsDTO metricsDTO = new MetricsDTO();
		metricsDTO.setImportedAt(System.currentTimeMillis());

		EventDTO absenceEventDto = new EventDTO();
		absenceEventDto.setId(UUID.randomUUID());
		absenceEventDto.setEventType(eventType);
		absenceEventDto.setEventTimestamp(eventTimestamp);
		absenceEventDto.setSource("absence-detection-service");
		absenceEventDto.setPayload(objectMapper.writeValueAsString(payload));
		absenceEventDto.setMetrics(metricsDTO);
		return absenceEventDto;
	}
}