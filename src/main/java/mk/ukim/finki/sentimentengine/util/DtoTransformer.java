package mk.ukim.finki.sentimentengine.util;

import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.data.dto.MetricsDTO;
import mk.ukim.finki.sentimentengine.data.dto.PendingCountDTO;
import mk.ukim.finki.sentimentengine.data.dto.RuleDetailDTO;
import mk.ukim.finki.sentimentengine.data.dto.RuleListItemDTO;
import mk.ukim.finki.sentimentengine.data.entity.SentimentRule;

import java.util.UUID;

/**
 * Centralized DTO transformation methods.
 *
 * @author kristina
 */
public final class DtoTransformer {

	private DtoTransformer() {
	}

	public static RuleDetailDTO toRuleDetailDTO(SentimentRule rule) {
		return new RuleDetailDTO(
			rule.getId(),
			rule.getEventType(),
			rule.getRuleType(),
			rule.getRuleDefinition(),
			rule.getBaseScore(),
			rule.getExplanation(),
			rule.getVersion(),
			rule.getCreatedAt()
		);
	}

	public static RuleListItemDTO toRuleListItemDTO(SentimentRule rule, long assignedCount, long pendingCount) {
		return new RuleListItemDTO(
			rule.getId(),
			rule.getEventType(),
			rule.getRuleType(),
			rule.getBaseScore(),
			rule.getVersion(),
			rule.getExplanation(),
			rule.getCreatedAt(),
			assignedCount,
			pendingCount
		);
	}

	public static PendingCountDTO toPendingCountDTO(Object[] row) {
		return new PendingCountDTO((String) row[0], (Long) row[1]);
	}

	public static EventDTO toEventDTO(String eventType, Long timestamp, String source, String payload) {
		MetricsDTO metricsDTO = new MetricsDTO();
		metricsDTO.setImportedAt(System.currentTimeMillis());
		UUID eventId = UUID.randomUUID();
		return new EventDTO(eventId, eventType, timestamp, source, payload, metricsDTO);
	}
}
