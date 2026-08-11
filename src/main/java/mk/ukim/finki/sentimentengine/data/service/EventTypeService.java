package mk.ukim.finki.sentimentengine.data.service;

import mk.ukim.finki.sentimentengine.data.entity.EventType;
import mk.ukim.finki.sentimentengine.data.repository.EventTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author kristina
 */
@Service
public class EventTypeService extends GenericEntityService<EventType, EventTypeRepository> {
	public EventTypeService(EventTypeRepository repository) {
		super(repository);
	}

	@Override
	protected Logger getLogger() {
		return LoggerFactory.getLogger(EventTypeService.class);
	}

	public EventType findByName(String eventType) {
		return getRepository().findByName(eventType);
	}

	public List<EventType> findWithoutRules() {
		return getRepository().findByHasRuleFalse();
	}
}
