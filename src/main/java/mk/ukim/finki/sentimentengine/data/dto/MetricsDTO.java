package mk.ukim.finki.sentimentengine.data.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Time metrics for tracking internal performances
 *
 * @author kristina
 */
public class MetricsDTO implements Serializable {
	@Serial
	private static final long serialVersionUID = 0L;
	private Long importedAt;
	private Long receivedAt;
	private Long finishedProcessingAt;
	// add other metrics here

	public MetricsDTO() {
	}

	public MetricsDTO(Long importedAt, Long receivedAt, Long finishedProcessingAt) {
		this.importedAt = importedAt;
		this.receivedAt = receivedAt;
		this.finishedProcessingAt = finishedProcessingAt;
	}

	public Long getImportedAt() {
		return importedAt;
	}

	public void setImportedAt(Long importedAt) {
		this.importedAt = importedAt;
	}

	public Long getReceivedAt() {
		return receivedAt;
	}

	public void setReceivedAt(Long receivedAt) {
		this.receivedAt = receivedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		MetricsDTO that = (MetricsDTO) o;
		return Objects.equals(importedAt, that.importedAt) && Objects.equals(receivedAt, that.receivedAt) && Objects.equals(finishedProcessingAt, that.finishedProcessingAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(importedAt, receivedAt, finishedProcessingAt);
	}

	@Override
	public String toString() {
		return "MetricsDTO{" +
			"importedAt=" + importedAt +
			", receivedAt=" + receivedAt +
			", finishedProcessingAt=" + finishedProcessingAt +
			'}';
	}

	public Long getFinishedProcessingAt() {
		return finishedProcessingAt;
	}

	public void setFinishedProcessingAt(Long finishedProcessingAt) {
		this.finishedProcessingAt = finishedProcessingAt;
	}
}
