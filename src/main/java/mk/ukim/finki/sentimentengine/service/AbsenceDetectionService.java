package mk.ukim.finki.sentimentengine.service;

import io.micrometer.core.annotation.Timed;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.data.dto.MetricsDTO;
import mk.ukim.finki.sentimentengine.data.entity.EventType;
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
@RequiredArgsConstructor
public class AbsenceDetectionService {

	public static final String ABSENCE_EVENT_TYPE = "system.absence.";
	public static final String GLOBAL_ABSENCE_EVENT_TYPE = ABSENCE_EVENT_TYPE + "global";
	public static final String TYPE_ABSENCE_EVENT_TYPE = ABSENCE_EVENT_TYPE + "type";
	private static final Logger log = LoggerFactory.getLogger(AbsenceDetectionService.class);
	private final EventTypeRegistry eventTypeRegistry;
	private final InternalBufferProducer bufferProducer;
	private final RawEventService rawEventService;
	private final ObjectMapper objectMapper;

	private final Set<String> perTypeAbsenceFired = ConcurrentHashMap.newKeySet();
	// De-dup keys for gaps already reported by the scheduled per-type job: "<type>@<gapStartTs>".
	private final Set<String> reportedGapKeys = ConcurrentHashMap.newKeySet();
	private final AtomicLong lastTimestampReceived = new AtomicLong(0);
	private volatile boolean globalAbsenceFired = false;
	@Value("${absence.check.on.arrival.enabled:true}")
	private boolean checkAbsenceOnArrivalEnabled;

	@Value("${absence.threshold-ms.global:300000}")
	private int absenceGlobalThresholdMs;

	@Value("${absence.threshold-ms.per-type:600000}")
	private int absencePerTypeThresholdMs;

	@Value("${absence.clock-mode:EVENT_TIME}")
	private String absenceClockMode;

	@PostConstruct
	public void init() {
		Long lastTimestamp = rawEventService.findLastTimestamp();
		lastTimestampReceived.set(lastTimestamp != null ? lastTimestamp : 0L);
		log.info("AbsenceDetectionService initialized: lastTimestampReceived={}, globalAbsenceThreshold={}," +
			" perTypeAbsenceThreshold={}", lastTimestampReceived.get(), absenceGlobalThresholdMs, absencePerTypeThresholdMs);
	}


	public void updateLastReceivedTimestamp(long timestamp) {
		lastTimestampReceived.updateAndGet(current -> Math.max(current, timestamp));
	}

	private long resolveNow(long now) {
		if ("REAL_TIME".equalsIgnoreCase(absenceClockMode)) {
			return now;
		}
		return lastTimestampReceived.get();
	}

	@Timed(value = "sentiment.absence.check.duration", description = "Global absence check duration")
	public void checkGlobalAbsence(long currentTime) {
		long now = resolveNow(currentTime);
		long lastTimestamp = lastTimestampReceived.get();

		if (lastTimestamp == 0) {
			log.debug("No events received yet, skipping global absence check");
			return;
		}

		long gap = now - lastTimestamp;
		if (gap > absenceGlobalThresholdMs) {
			if (!globalAbsenceFired) {
				EventDTO absenceEventDto = this.createAbsenceEventDto(GLOBAL_ABSENCE_EVENT_TYPE, lastTimestamp, gap, now, false);
				bufferProducer.sendToBuffer(absenceEventDto);
				globalAbsenceFired = true;

				log.info("[ABSENCE-DETECTION][GLOBAL] Detected global absence, lastReceivedTimestamp:{}, gapDurationMs:{}", lastTimestamp, gap);
			}
		} else {
			globalAbsenceFired = false; // reset flag only when a real event has arrived
			log.info("[ABSENCE-DETECTION][GLOBAL] No absence detected");
		}
	}

	/**
	 * Scheduled per-type absence detection.
	 * <p>For each known (non-absence) event type this scans the type's event timestamps in ascending
	 * order (sorted in the DB, so it is independent of the order in which events were imported) and
	 * fires one absence event for every internal gap between consecutive events that exceeds the
	 * per-type threshold. This is what catches gaps for out-of-order / bulk-imported data, which the
	 * on-arrival path cannot (there, an out-of-order older event yields a negative gap and is ignored).
	 * <p>In addition to internal gaps, a trailing gap between the type's last event and "now" is
	 * checked. "now" is governed by the clock mode ({@code EVENT_TIME} = data frontier,
	 * {@code REAL_TIME} = wall clock) via {@link #resolveNow(long)}.
	 * <p>Each reported gap is de-duplicated by (type, gap-start timestamp) so repeated job runs do
	 * not re-emit the same gap.
	 */
	public void checkPerTypeAbsence(long currentTime) {
		long now = resolveNow(currentTime);
		List<EventType> allTypes = eventTypeRegistry.getAllEventTypes();

		// skip absence event types — they are outputs, not inputs for detection
		for (EventType type : allTypes) {
			String typeName = type.getName();
			if (typeName.contains(ABSENCE_EVENT_TYPE))
				continue;

			List<Long> timestamps = rawEventService.findTimestampsByEventTypeOrdered(typeName);
			if (timestamps.isEmpty()) {
				continue;
			}

			// Internal gaps between consecutive events (order-independent thanks to DB sort).
			for (int i = 1; i < timestamps.size(); i++) {
				long prev = timestamps.get(i - 1);
				long next = timestamps.get(i);
				long gap = next - prev;
				if (gap > absencePerTypeThresholdMs) {
					firePerTypeAbsenceGap(typeName, prev, gap, prev + absencePerTypeThresholdMs);
				}
			}

			// Trailing gap: type may have gone quiet after its last event (governed by clock mode).
			long lastSeen = timestamps.get(timestamps.size() - 1);
			long trailingGap = now - lastSeen;
			if (trailingGap > absencePerTypeThresholdMs) {
				firePerTypeAbsenceGap(typeName, lastSeen, trailingGap, now);
			}
		}
	}

	/**
	 * Emits a per-type absence event for a detected gap, de-duplicated by (type, gap-start) so the
	 * same gap is not re-reported on subsequent job runs.
	 */
	private void firePerTypeAbsenceGap(String typeName, long gapStart, long gap, long eventTimestamp) {
		String gapKey = typeName + "@" + gapStart;
		if (!reportedGapKeys.add(gapKey)) {
			return; // already reported this gap
		}
		String messageType = TYPE_ABSENCE_EVENT_TYPE + "." + typeName;
		EventDTO absenceEventDto = this.createAbsenceEventDto(messageType, gapStart, gap, eventTimestamp, false);
		bufferProducer.sendToBuffer(absenceEventDto);
		log.info("[ABSENCE-DETECTION][TYPE] Detected absence for event type:{}, gapStart={}, gapDurationMs={}",
			typeName, gapStart, gap);
	}


	@Timed(value = "sentiment.absence.check.duration", description = "Absence detection check duration")
	public void checkForAbsenceOnArrival(Long eventTimestamp, String eventType) {
		if (!checkAbsenceOnArrivalEnabled) {
			return;
		}
		if (eventType.startsWith(ABSENCE_EVENT_TYPE)) {
			return; // skip absence events to avoid recursive double-prefixing
		}
		long lastTimestamp = lastTimestampReceived.get();

		if (lastTimestamp == 0) {
			return; // first event
		}

		// Only measure forward gaps. Under concurrent import events can arrive out of timestamp
		// order; an out-of-order (older) event must not be treated as a gap.
		long gap = eventTimestamp - lastTimestamp;

		// GLOBAL
		if (gap > absenceGlobalThresholdMs) {
			if (!globalAbsenceFired) {
				EventDTO absenceEventDto = this.createAbsenceEventDto(GLOBAL_ABSENCE_EVENT_TYPE, lastTimestamp, gap,
					lastTimestamp + absenceGlobalThresholdMs, true);
				bufferProducer.sendToBuffer(absenceEventDto);
				globalAbsenceFired = true;

				log.info("[ABSENCE-DETECTION][GLOBAL] Detected global absence on arrival, lastReceivedTimestamp:{}, gapDurationMs:{}", lastTimestamp, gap);
			}
		} else if (gap >= 0) {
			// Event arrived within the global threshold: the stream is active again, so re-arm the
			// latch. Without this reset the latch would stay stuck after the first fire and only one
			// global absence would ever be emitted (the "exactly one per type" symptom).
			globalAbsenceFired = false;
		}

		// TYPE
		long perTypeLastSeen = eventTypeRegistry.getLastSeenAt(eventType);
		if (perTypeLastSeen > 0) {
			long perTypeGap = eventTimestamp - perTypeLastSeen;
			if (perTypeGap > absencePerTypeThresholdMs) {
				if (!perTypeAbsenceFired.contains(eventType)) {
					String messageType = TYPE_ABSENCE_EVENT_TYPE + "." + eventType;
					EventDTO typeAbsenceEventDto = this.createAbsenceEventDto(messageType, perTypeLastSeen, perTypeGap,
						perTypeLastSeen + absencePerTypeThresholdMs, true);
					bufferProducer.sendToBuffer(typeAbsenceEventDto);
					perTypeAbsenceFired.add(eventType);

					log.info("[ABSENCE-DETECTION][TYPE] Detected absence on arrival for event type:{}, lastSeenAt={}, gapDurationMs={}",
						eventType, perTypeLastSeen, perTypeGap);
				}
			} else if (perTypeGap >= 0) {
				// This type is active again within its threshold: re-arm its latch so a later genuine
				// gap for the same type can fire again.
				perTypeAbsenceFired.remove(eventType);
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
