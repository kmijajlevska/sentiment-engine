package mk.ukim.finki.sentimentengine.data.dto;

import java.io.Serializable;

/**
 * Output DTO representing a single processed event in detail responses.
 *
 * @author kristina
 */
public record ProcessedEventDTO(
    Long id,
    Long eventId,
    String eventType,
    long eventTimestamp,
    double sentimentScore,
    double confidence,
    Long appliedRuleId
) implements Serializable {}
