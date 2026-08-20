package mk.ukim.finki.sentimentengine.data.dto;

import java.io.Serializable;

/**
 * Request DTO for creating or updating a sentiment rule.
 *
 * @author kristina
 */
public record RuleRequestDTO(
    String eventType,
    String ruleType,
    String ruleDefinition,
    double baseScore,
    String explanation
) implements Serializable {}
