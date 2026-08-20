package mk.ukim.finki.sentimentengine.data.dto;

import java.io.Serializable;
import java.util.Date;

/**
 * Response DTO for the rule list endpoint with computed counts.
 *
 * @author kristina
 */
public record RuleListItemDTO(
    Long id,
    String eventType,
    String ruleType,
    double baseScore,
    int version,
    String explanation,
    Date createdAt,
    long assignedEventCount,
    long pendingEventCount
) implements Serializable {}
