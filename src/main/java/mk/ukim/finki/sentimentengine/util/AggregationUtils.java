package mk.ukim.finki.sentimentengine.util;

import mk.ukim.finki.sentimentengine.data.dto.BucketMetricsDTO;
import mk.ukim.finki.sentimentengine.data.dto.ProcessedEventDTO;
import mk.ukim.finki.sentimentengine.data.entity.ProcessedEvent;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static mk.ukim.finki.sentimentengine.service.AbsenceDetectionService.ABSENCE_EVENT_TYPE;

/**
 * Utility methods for aggregation computations and type conversions.
 *
 * @author kristina
 */
public final class AggregationUtils {

	private AggregationUtils() {
	}

	public static Date toDateTruncatedToDay(long epochMillis) {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(epochMillis);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
	}

	public static Date toDateTruncatedToWeek(long epochMillis) {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(epochMillis);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.setFirstDayOfWeek(Calendar.MONDAY);
		cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
		return cal.getTime();
	}

	public static Date toDateTruncatedToMonth(long epochMillis) {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(epochMillis);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
	}


	public static BucketMetricsDTO mapRowToBucketMetrics(Object[] row) {
		long bucketStart = toEpochMillis(row[0]);
		long eventCount = toLong(row[1]);
		double avgSentiment = toDouble(row[2]);
		double minSentiment = toDouble(row[3]);
		double maxSentiment = toDouble(row[4]);
		long absenceCount = toLong(row[5]);

		return new BucketMetricsDTO(bucketStart, eventCount, avgSentiment, minSentiment, maxSentiment, absenceCount);
	}

	public static long toEpochMillis(Object value) {
		if (value instanceof Timestamp ts) {
			return ts.getTime();
		} else if (value instanceof java.sql.Date d) {
			return d.getTime();
		} else if (value instanceof Date d) {
			return d.getTime();
		} else if (value instanceof java.time.LocalDate ld) {
			return ld.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
		} else if (value instanceof Number n) {
			return n.longValue();
		}
		throw new IllegalArgumentException("Cannot convert to epoch millis: " + (value == null ? "null" : value.getClass()));
	}

	public static long toLong(Object value) {
		if (value instanceof Number n) {
			return n.longValue();
		}
		return 0L;
	}

	public static double toDouble(Object value) {
		if (value instanceof Number n) {
			return n.doubleValue();
		}
		return 0.0;
	}

	public static ProcessedEventDTO toDto(ProcessedEvent event) {
		return new ProcessedEventDTO(
			event.getId(),
			event.getEventId(),
			event.getEventType(),
			event.getEventTimestamp(),
			event.getSentimentScore().doubleValue(),
			event.getConfidence().doubleValue(),
			event.getAppliedRuleId());
	}

	public static BucketMetricsDTO computeSummary(long bucketStart, List<ProcessedEvent> events) {
		long count = events.size();
		double sum = 0.0;
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		long absenceCount = 0;

		for (ProcessedEvent e : events) {
			double score = e.getSentimentScore().doubleValue();
			sum += score;
			if (score < min) min = score;
			if (score > max) max = score;
			if (e.getEventId() != null && e.getEventType().startsWith(ABSENCE_EVENT_TYPE)) {
				absenceCount++;
			}
		}

		double avg = count > 0 ? sum / count : 0.0;
		if (count == 0) {
			min = 0.0;
			max = 0.0;
		}

		return new BucketMetricsDTO(bucketStart, count, avg, min, max, absenceCount);
	}
}
