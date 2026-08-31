package mk.ukim.finki.sentimentengine.service;

import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.data.service.RawEventService;
import mk.ukim.finki.sentimentengine.messaging.InternalBufferProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AbsenceDetectionService}.
 *
 * <p>Covers the absence-detection feature described in section 3.6: the system treats the absence
 * of expected events as a significant signal. Absence is detected both globally and per event type,
 * on arrival of a new event and via the scheduled job, and must not fire duplicate events for one
 * continuous gap.
 */
@ExtendWith(MockitoExtension.class)
class AbsenceDetectionServiceTest {

	private static final int GLOBAL_THRESHOLD_MS = 300000;   // 5 min
	private static final int PER_TYPE_THRESHOLD_MS = 600000;  // 10 min

	@Mock
	private EventTypeRegistry eventTypeRegistry;
	@Mock
	private InternalBufferProducer bufferProducer;
	@Mock
	private RawEventService rawEventService;

	private AbsenceDetectionService service;

	@BeforeEach
	void setUp() {
		service = new AbsenceDetectionService(eventTypeRegistry, bufferProducer, rawEventService, new ObjectMapper());
		ReflectionTestUtils.setField(service, "checkAbsenceOnArrivalEnabled", true);
		ReflectionTestUtils.setField(service, "absenceGlobalThresholdMs", GLOBAL_THRESHOLD_MS);
		ReflectionTestUtils.setField(service, "absencePerTypeThresholdMs", PER_TYPE_THRESHOLD_MS);
		ReflectionTestUtils.setField(service, "absenceClockMode", "REAL_TIME");
	}

	@Test
	@DisplayName("checkGlobalAbsence fires a global absence event when the gap exceeds the threshold")
	void globalAbsenceFiresWhenGapExceedsThreshold() {
		long lastTs = 1000000L;
		service.updateLastReceivedTimestamp(lastTs);

		service.checkGlobalAbsence(lastTs + GLOBAL_THRESHOLD_MS + 1);

		ArgumentCaptor<EventDTO> captor = ArgumentCaptor.forClass(EventDTO.class);
		verify(bufferProducer).sendToBuffer(captor.capture());
		assertThat(captor.getValue().getEventType()).isEqualTo(AbsenceDetectionService.GLOBAL_ABSENCE_EVENT_TYPE);
	}

	@Test
	@DisplayName("checkGlobalAbsence does nothing while within the threshold")
	void globalAbsenceSilentWhenWithinThreshold() {
		long lastTs = 1000000L;
		service.updateLastReceivedTimestamp(lastTs);

		service.checkGlobalAbsence(lastTs + GLOBAL_THRESHOLD_MS - 1);

		verifyNoInteractions(bufferProducer);
	}

	@Test
	@DisplayName("checkGlobalAbsence skips when no event has ever been received")
	void globalAbsenceSkippedWithoutAnyEvent() {
		service.checkGlobalAbsence(System.currentTimeMillis());

		verifyNoInteractions(bufferProducer);
	}

	@Test
	@DisplayName("A continuous global gap produces only a single absence event")
	void globalAbsenceIsNotDuplicatedForOneContinuousGap() {
		long lastTs = 1000000L;
		service.updateLastReceivedTimestamp(lastTs);

		service.checkGlobalAbsence(lastTs + GLOBAL_THRESHOLD_MS + 1);
		service.checkGlobalAbsence(lastTs + GLOBAL_THRESHOLD_MS + 5000);

		verify(bufferProducer, times(1)).sendToBuffer(any(EventDTO.class));
	}

	@Test
	@DisplayName("checkPerTypeAbsence fires per-type absence for a stale type")
	void perTypeAbsenceFiresForStaleType() {
		long now = 10000000L;
		EventType staleType = EventType.builder()
		                               .name("PushEvent")
		                               .lastSeenAt(now - PER_TYPE_THRESHOLD_MS - 1)
		                               .build();
		when(eventTypeRegistry.getAllEventTypes()).thenReturn(List.of(staleType));

		service.checkPerTypeAbsence(now);

		ArgumentCaptor<EventDTO> captor = ArgumentCaptor.forClass(EventDTO.class);
		verify(bufferProducer).sendToBuffer(captor.capture());
		assertThat(captor.getValue().getEventType())
			.isEqualTo(AbsenceDetectionService.TYPE_ABSENCE_EVENT_TYPE + ".PushEvent");
	}

	@Test
	@DisplayName("checkPerTypeAbsence ignores absence-type events (they are outputs, not inputs)")
	void perTypeAbsenceIgnoresAbsenceTypes() {
		long now = 10000000L;
		EventType absenceType = EventType.builder()
		                                 .name(AbsenceDetectionService.GLOBAL_ABSENCE_EVENT_TYPE)
		                                 .lastSeenAt(now - PER_TYPE_THRESHOLD_MS - 1)
		                                 .build();
		when(eventTypeRegistry.getAllEventTypes()).thenReturn(List.of(absenceType));

		service.checkPerTypeAbsence(now);

		verifyNoInteractions(bufferProducer);
	}

	@Test
	@DisplayName("On-arrival check fires a global absence when the new event arrives after a long gap")
	void onArrivalDetectsGlobalAbsence() {
		long lastTs = 5000000L;
		service.updateLastReceivedTimestamp(lastTs);
		when(eventTypeRegistry.getLastSeenAt("PushEvent")).thenReturn(0L);

		long arrivalTs = lastTs + GLOBAL_THRESHOLD_MS + 1;
		service.checkForAbsenceOnArrival(arrivalTs, "PushEvent");

		ArgumentCaptor<EventDTO> captor = ArgumentCaptor.forClass(EventDTO.class);
		verify(bufferProducer).sendToBuffer(captor.capture());
		assertThat(captor.getValue().getEventType()).isEqualTo(AbsenceDetectionService.GLOBAL_ABSENCE_EVENT_TYPE);
	}

	@Test
	@DisplayName("On-arrival check ignores absence events to avoid recursion")
	void onArrivalIgnoresAbsenceEvents() {
		service.updateLastReceivedTimestamp(1000L);

		service.checkForAbsenceOnArrival(10000000L, AbsenceDetectionService.GLOBAL_ABSENCE_EVENT_TYPE);

		verifyNoInteractions(bufferProducer);
	}

	@Test
	@DisplayName("On-arrival check is a no-op for the very first event")
	void onArrivalNoOpForFirstEvent() {
		service.checkForAbsenceOnArrival(10000000L, "PushEvent");

		verifyNoInteractions(bufferProducer);
	}

	@Test
	@DisplayName("On-arrival check can be disabled via configuration")
	void onArrivalDisabled() {
		ReflectionTestUtils.setField(service, "checkAbsenceOnArrivalEnabled", false);
		service.updateLastReceivedTimestamp(1000L);

		service.checkForAbsenceOnArrival(10000000L, "PushEvent");

		verifyNoInteractions(bufferProducer);
	}

	@Test
	@DisplayName("EVENT_TIME mode: scheduled global check ignores real-time and uses the data frontier (no false global absence for a completed dump)")
	void eventTimeModeGlobalUsesDataFrontier() {
		ReflectionTestUtils.setField(service, "absenceClockMode", "EVENT_TIME");
		// Frontier is the latest ingested (historical) timestamp.
		long frontier = 1_000_000L;
		service.updateLastReceivedTimestamp(frontier);

		// Real-time "now" is far in the future, as it would be when replaying an old dump.
		service.checkGlobalAbsence(frontier + 10L * 365 * 24 * 3600 * 1000);

		// Gap is measured against the frontier (== frontier), so no spurious global absence fires.
		verifyNoInteractions(bufferProducer);
	}

	@Test
	@DisplayName("EVENT_TIME mode: per-type absence fires against the data frontier, not real-time")
	void eventTimeModePerTypeUsesDataFrontier() {
		ReflectionTestUtils.setField(service, "absenceClockMode", "EVENT_TIME");
		long frontier = 10_000_000L;
		service.updateLastReceivedTimestamp(frontier);

		// This type went quiet well before the frontier -> genuine per-type absence within the dump.
		EventType staleType = EventType.builder()
		                               .name("PushEvent")
		                               .lastSeenAt(frontier - PER_TYPE_THRESHOLD_MS - 1)
		                               .build();
		when(eventTypeRegistry.getAllEventTypes()).thenReturn(List.of(staleType));

		//Real-time "now" (far future) is ignored in EVENT_TIME mode.
		service.checkPerTypeAbsence(System.currentTimeMillis());

		ArgumentCaptor<EventDTO> captor = ArgumentCaptor.forClass(EventDTO.class);
		verify(bufferProducer).sendToBuffer(captor.capture());
		assertThat(captor.getValue().getEventType())
			.isEqualTo(AbsenceDetectionService.TYPE_ABSENCE_EVENT_TYPE + ".PushEvent");
	}

	@Test
	@DisplayName("On-arrival re-arms the per-type latch so a later gap for the same type fires again")
	void onArrivalReArmsPerTypeLatchForRepeatedGaps() {
		long base = 5_000_000L;
		service.updateLastReceivedTimestamp(base);

		// First gap for PushEvent -> fires one per-type absence.
		when(eventTypeRegistry.getLastSeenAt("PushEvent")).thenReturn(base);
		long firstArrival = base + PER_TYPE_THRESHOLD_MS + 1;
		service.checkForAbsenceOnArrival(firstArrival, "PushEvent");

		// Type is active again shortly after -> latch re-arms (within threshold).
		when(eventTypeRegistry.getLastSeenAt("PushEvent")).thenReturn(firstArrival);
		service.updateLastReceivedTimestamp(firstArrival);
		service.checkForAbsenceOnArrival(firstArrival + 1000L, "PushEvent");

		// A second genuine gap later -> must fire again (not latched at one).
		long secondLastSeen = firstArrival + 1000L;
		when(eventTypeRegistry.getLastSeenAt("PushEvent")).thenReturn(secondLastSeen);
		service.updateLastReceivedTimestamp(secondLastSeen);
		service.checkForAbsenceOnArrival(secondLastSeen + PER_TYPE_THRESHOLD_MS + 1, "PushEvent");

		verify(bufferProducer, times(2)).sendToBuffer(
			argThat(dto -> dto.getEventType().equals(AbsenceDetectionService.TYPE_ABSENCE_EVENT_TYPE + ".PushEvent")));
	}
}
