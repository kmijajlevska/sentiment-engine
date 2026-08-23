package mk.ukim.finki.sentimentengine.data.repository;

import mk.ukim.finki.sentimentengine.data.entity.RawEvent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * @author kristina
 */
@Repository
public interface RawEventRepository extends GenericRepository<RawEvent> {

	@Query("SELECT MAX(e.timestamp) FROM RawEvent e")
	Optional<Long> findLatestTimestamp();
}
