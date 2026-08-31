package mk.ukim.finki.sentimentengine.data.repository;

import mk.ukim.finki.sentimentengine.data.entity.RawEvent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * @author kristina
 */
@Repository
public interface RawEventRepository extends GenericRepository<RawEvent> {

	@Query("SELECT MAX(e.timestamp) FROM RawEvent e")
	Optional<Long> findLatestTimestamp();

	@Query("SELECT DISTINCT e.timestamp FROM RawEvent e WHERE e.eventType = :eventType ORDER BY e.timestamp ASC")
	List<Long> findTimestampsByEventTypeOrderByTimestampAsc(@Param("eventType") String eventType);
}
