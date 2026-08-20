package mk.ukim.finki.sentimentengine.data.repository;

import mk.ukim.finki.sentimentengine.data.entity.SentimentRule;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author kristina
 */
@Repository
public interface SentimentRuleRepository extends GenericRepository<SentimentRule> {

	SentimentRule findTopByEventTypeOrderByVersionDesc(String eventType);

	List<SentimentRule> findByEventType(String eventType);

	List<SentimentRule> findAllByOrderByCreatedAtDesc();

}
