package mk.ukim.finki.sentimentengine.data.service;

import mk.ukim.finki.sentimentengine.data.entity.ProcessedEvent;
import mk.ukim.finki.sentimentengine.data.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * @author kristina
 */
@Service
public class ProcessedEventService extends GenericEntityService<ProcessedEvent, ProcessedEventRepository> {
	public ProcessedEventService(ProcessedEventRepository repository) {
		super(repository);
	}

	@Override
	protected Logger getLogger() {
		return LoggerFactory.getLogger(ProcessedEventService.class);
	}

	public List<Object[]> aggregateByMinute(String eventType, long from, long to) {
		return getRepository().aggregateByMinute(eventType, from, to);
	}

	public List<Object[]> aggregateByHour(String eventType, long from, long to) {
		return getRepository().aggregateByHour(eventType, from, to);
	}

	public List<Object[]> aggregateByDay(String eventType, Date from, Date to) {
		return getRepository().aggregateByDay(eventType, from, to);
	}

	public List<Object[]> aggregateByWeek(String eventType, Date from, Date to) {
		return getRepository().aggregateByWeek(eventType, from, to);
	}

	public List<Object[]> aggregateByMonth(String eventType, Date from, Date to) {
		return getRepository().aggregateByMonth(eventType, from, to);
	}


	public List<ProcessedEvent> findByEventTypeAndMinuteBucket(String eventType, long minuteBucket) {
		return getRepository().findByEventTypeAndMinuteBucket(eventType, minuteBucket);
	}

	public List<ProcessedEvent> findByEventTypeAndHourBucket(String eventType, long hourBucket) {
		return getRepository().findByEventTypeAndHourBucket(eventType, hourBucket);
	}

	public List<ProcessedEvent> findByEventTypeAndDayBucket(String eventType, Date dayBucket) {
		return getRepository().findByEventTypeAndDayBucket(eventType, dayBucket);
	}

	public List<ProcessedEvent> findByEventTypeAndWeekBucket(String eventType, Date weekBucket) {
		return getRepository().findByEventTypeAndWeekBucket(eventType, weekBucket);
	}

	public List<ProcessedEvent> findByEventTypeAndMonthBucket(String eventType, Date monthBucket) {
		return getRepository().findByEventTypeAndMonthBucket(eventType, monthBucket);
	}
}
