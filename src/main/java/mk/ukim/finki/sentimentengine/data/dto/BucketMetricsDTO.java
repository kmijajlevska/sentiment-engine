package mk.ukim.finki.sentimentengine.data.dto;

import java.io.Serializable;

/**
 * A single data point in a time aggregation response.
 * @author kristina
 */
public record BucketMetricsDTO(
    Long bucketStart,
    Long eventCount,
    Double avgSentiment,
    Double minSentiment,
    Double maxSentiment,
    Long absenceCount
) implements Serializable {}
