package mk.ukim.finki.sentimentengine.api;

import jakarta.validation.Valid;
import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.data.dto.gharchive.GhArchiveEvent;
import mk.ukim.finki.sentimentengine.messaging.InternalBufferProducer;
import mk.ukim.finki.sentimentengine.util.DataSourceTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * @author kristina
 */
@RestController
@RequestMapping("test")
public class DataTestController {

	private static final Logger logger = LoggerFactory.getLogger(DataTestController.class);

	private final InternalBufferProducer bufferProducer;
	private final ObjectMapper objectMapper;

	public DataTestController(InternalBufferProducer bufferProducer, ObjectMapper objectMapper) {
		this.bufferProducer = bufferProducer;
		this.objectMapper = objectMapper;
	}

	@PostMapping("/github-archive/event")
	public void importEvent(@Valid @RequestBody GhArchiveEvent ghArchiveEvent) {
		try {
			String eventType = ghArchiveEvent.type();
			Long timestamp = ghArchiveEvent.createdAt().toEpochMilli();
			String source = ghArchiveEvent.repo().name();
			String payload = objectMapper.writeValueAsString(ghArchiveEvent);
			EventDTO eventDTO = DataSourceTransformer.generateEvent(eventType, timestamp, source, payload);
			bufferProducer.sendToBuffer(eventDTO);
		} catch (Exception e) {
			logger.error("[API][TEST] Failed to produce test event: ", e);
		}
	}
}
