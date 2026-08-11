package mk.ukim.finki.sentimentengine.data.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Detailed view of a specific time bucket, containing individual events and summary statistics.
 *
 * @author kristina
 */
public record BucketDetailsDTO(
	List<ProcessedEventDTO> events,
	BucketMetricsDTO summary
) implements Serializable {
}
