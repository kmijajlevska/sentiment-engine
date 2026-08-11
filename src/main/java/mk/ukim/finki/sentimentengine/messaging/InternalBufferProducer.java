package mk.ukim.finki.sentimentengine.messaging;

import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Jms producer to the internal buffer queue.
 * Produces events from external sources to the internal processing components.
 *
 * @author kristina
 */
@Component
public class InternalBufferProducer {

	private static final Logger logger = LoggerFactory.getLogger(InternalBufferProducer.class.getName());
	private final JmsTemplate jmsTemplate;
	@Value("${internal.buffer.queue.name:event-buffer-queue}")
	private String queueName;

	public InternalBufferProducer(JmsTemplate jmsTemplate) {
		this.jmsTemplate = jmsTemplate;
	}

	public void sendToBuffer(EventDTO eventDTO) {
		if(eventDTO == null){
			logger.debug("[INTERNAL-BUFFER-PROCESSING][PRODUCER] Skipping null event..");
			return;
		}
		try {
			jmsTemplate.convertAndSend(queueName, eventDTO);
			logger.info("[INTERNAL-BUFFER-PROCESSING][PRODUCER] Event {} sent to buffer", eventDTO.getId());
		} catch (Exception e) {
			logger.error("[INTERNAL-BUFFER-PROCESSING][PRODUCER] Failed to send event {} to buffer", eventDTO.getId(), e);

		}
	}

}
