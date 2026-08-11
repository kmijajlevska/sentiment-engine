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

	@Query("SELECT e FROM RawEvent e WHERE e.eventType = :type AND e.timestamp BETWEEN :from AND :to ORDER BY e.timestamp ASC")
	List<RawEvent> findByTypeAndTimeRange(
		@Param("type") String type,
		@Param("from") long from,
		@Param("to") long to);

	@Query("SELECT e.eventType, COUNT(e) FROM RawEvent e WHERE e.timestamp BETWEEN :from AND :to GROUP BY e.eventType")
	List<Object[]> countByTypeInRange(
		@Param("from") long from,
		@Param("to") long to);

	@Query("SELECT MAX(e.timestamp) FROM RawEvent e")
	Optional<Long> findLatestTimestamp();
}
