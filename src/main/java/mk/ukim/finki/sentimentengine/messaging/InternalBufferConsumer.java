package mk.ukim.finki.sentimentengine.messaging;


import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.service.EventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Jms consumer of the internal buffer queue.
 * Consumes events from external sources and pass them to the internal processing components.
 *
 * @author kristina
 */
@Component
public class InternalBufferConsumer {

	private static final Logger logger = LoggerFactory.getLogger(InternalBufferConsumer.class.getName());

	private final EventProcessor eventProcessor;

	public InternalBufferConsumer(EventProcessor eventProcessor) {
		this.eventProcessor = eventProcessor;
	}

	@JmsListener(destination = "${internal.buffer.queue.name:event-buffer-queue}", concurrency = "3-10")
	public void onMessage(EventDTO event) {
		try {
			if (event == null) {
				logger.debug("[INTERNAL-BUFFER-PROCESSING][CONSUMER] Received null event, discarding");
				return;
			}
			if (event.getMetrics() != null) {
				event.getMetrics().setReceivedAt(System.currentTimeMillis());
			}
			logger.info("[INTERNAL-BUFFER-PROCESSING][CONSUMER] Event {} received on buffer. Passing for further processing", event.getId());
			eventProcessor.onEvent(event);
		} catch (Exception e) {
			logger.error("[INTERNAL-BUFFER-PROCESSING][CONSUMER] Failed to process JMS message: {}", e.getMessage(), e);
		}
	}
}
