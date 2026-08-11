package mk.ukim.finki.sentimentengine.data.service;

import mk.ukim.finki.sentimentengine.data.entity.RawEvent;
import mk.ukim.finki.sentimentengine.data.repository.RawEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author kristina
 */
@Service
public class RawEventService extends GenericEntityService<RawEvent, RawEventRepository> {

	public RawEventService(RawEventRepository repository) {
		super(repository);
	}

	@Override
	protected Logger getLogger() {
		return LoggerFactory.getLogger(RawEventService.class);
	}

	public Long findLastTimestamp() {
		return getRepository().findLatestTimestamp().orElse(null);
	}

	public List<RawEvent> findByTypeAndTimeRange(String eventType, long from, long to) {
		return getRepository().findByTypeAndTimeRange(eventType, from, to);
	}

	public List<Object[]> countByTypeInRange(long from, long to) {
		return getRepository().countByTypeInRange(from, to);
	}
}
