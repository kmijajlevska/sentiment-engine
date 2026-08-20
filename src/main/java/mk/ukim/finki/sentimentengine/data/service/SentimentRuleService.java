package mk.ukim.finki.sentimentengine.data.service;

import mk.ukim.finki.sentimentengine.data.entity.SentimentRule;
import mk.ukim.finki.sentimentengine.data.repository.SentimentRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author kristina
 */
@Service
public class SentimentRuleService extends GenericEntityService<SentimentRule, SentimentRuleRepository> {
	public SentimentRuleService(SentimentRuleRepository repository) {
		super(repository);
	}

	@Override
	protected Logger getLogger() {
		return LoggerFactory.getLogger(SentimentRuleService.class);
	}

	public SentimentRule findTopByEventTypeOrderByVersionDesc(String eventType) {
		return getRepository().findTopByEventTypeOrderByVersionDesc(eventType);
	}

	public List<SentimentRule> findAllByOrderByCreatedAtDesc() {
		return getRepository().findAllByOrderByCreatedAtDesc();
	}

	public void deleteById(Long id) {
		getRepository().deleteById(id);
	}
}
