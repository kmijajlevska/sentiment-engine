package mk.ukim.finki.sentimentengine.data.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Time metrics for tracking internal performances
 *
 * @author kristina
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricsDTO implements Serializable {
	@Serial
	private static final long serialVersionUID = 0L;
	private Long importedAt;
	private Long receivedAt;
	private Long finishedProcessingAt;
	// add other metrics here
}
