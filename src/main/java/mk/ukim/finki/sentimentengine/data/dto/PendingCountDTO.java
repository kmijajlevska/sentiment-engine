package mk.ukim.finki.sentimentengine.data.dto;

import java.io.Serializable;

/**
 * Response DTO for pending event counts per event type.
 *
 * @author kristina
 */
public record PendingCountDTO(
    String eventType,
    long pendingCount
) implements Serializable {}
