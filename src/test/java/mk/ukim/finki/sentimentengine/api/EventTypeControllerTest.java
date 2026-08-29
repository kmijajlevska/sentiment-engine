package mk.ukim.finki.sentimentengine.api;

import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.service.EventTypeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@link EventTypeController} using the MVC slice.
 *
 * <p>Supports scenario 4.3, where the event-type selector in the analytics UI is populated from the
 * registered event types. This read-only endpoint exposes those types.
 */
@WebMvcTest(EventTypeController.class)
class EventTypeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EventTypeRegistry eventTypeRegistry;

	@Test
	@DisplayName("GET /event-types returns the registered event types")
	void listEventTypes() throws Exception {
		EventType type = EventType.builder()
		                          .name("PushEvent")
		                          .firstSeenAt(1L)
		                          .lastSeenAt(2L)
		                          .occurrenceCount(10L)
		                          .hasRule(true)
		                          .build();
		when(eventTypeRegistry.getAllEventTypes()).thenReturn(List.of(type));

		mockMvc.perform(get("/event-types"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$[0].name").value("PushEvent"))
		       .andExpect(jsonPath("$[0].hasRule").value(true));
	}
}
