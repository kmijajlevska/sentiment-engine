package mk.ukim.finki.sentimentengine.data.repository;

import mk.ukim.finki.sentimentengine.data.entity.EventType;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author kristina
 */
@Repository
public interface EventTypeRepository extends GenericRepository<EventType> {

	List<EventType> findByHasRuleFalse();

	EventType findByName(String name);

	List<EventType> findByLastSeenAtBefore(long cutoff);
}
