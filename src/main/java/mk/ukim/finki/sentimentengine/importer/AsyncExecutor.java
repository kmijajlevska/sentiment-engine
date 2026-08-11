package mk.ukim.finki.sentimentengine.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * @author kristina
 */
@Component
public class AsyncExecutor {

	private static final Logger log = LoggerFactory.getLogger(AsyncExecutor.class);

	@Async
	public void loadFileAsync(FileDataSource dataSource, Path filePath) {
		log.info("[{}] Async load started for: {}", dataSource.getName(), filePath);
		int count = dataSource.loadFile(filePath);
		log.info("[{}] Async load completed for: {} — {} events loaded",
			dataSource.getName(), filePath, count);
	}

	@Async
	public void loadBatchAsync(FileDataSource dataSource, List<Path> filePaths) {
		log.info("[{}] Async batch load started for {} files",
			dataSource.getName(), filePaths.size());
		int totalCount = dataSource.loadFiles(filePaths);
		log.info("[{}] Async batch load completed — {} total events from {} files",
			dataSource.getName(), totalCount, filePaths.size());
	}
}