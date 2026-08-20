package mk.ukim.finki.sentimentengine.api;

import lombok.RequiredArgsConstructor;
import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.service.EventTypeRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author kristina
 */
@RestController
@RequestMapping("event-types")
@RequiredArgsConstructor
public class EventTypeController {

	private final EventTypeRegistry eventTypeRegistry;

	@GetMapping
	public List<EventType> getAllEventTypes() {
		return eventTypeRegistry.getAllEventTypes();
	}

}
