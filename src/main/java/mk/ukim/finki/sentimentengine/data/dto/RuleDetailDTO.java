package mk.ukim.finki.sentimentengine.data.dto;

import java.io.Serializable;
import java.util.Date;

/**
 * Response DTO for single rule detail.
 *
 * @author kristina
 */
public record RuleDetailDTO(
    Long id,
    String eventType,
    String ruleType,
    String ruleDefinition,
    double baseScore,
    String explanation,
    int version,
    Date createdAt
) implements Serializable {}
