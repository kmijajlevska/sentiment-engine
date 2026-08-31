package mk.ukim.finki.sentimentengine.importer;

import mk.ukim.finki.sentimentengine.data.dto.EventDTO;

/**
 * Abstraction for event data sources.
 * The data sources could be a file, a database, an API or a message broker.
 * All sources pass RawEvents to the internal broker
 *
 * @author kristina
 */
public interface EventDataSource {

	void start();

	void stop();

	String getName();

	boolean isRunning();

	void sendToBuffer(EventDTO eventDTO);
}
