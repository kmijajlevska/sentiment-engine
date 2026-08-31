package mk.ukim.finki.sentimentengine.importer;

import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.data.dto.gharchive.GhArchiveEvent;
import mk.ukim.finki.sentimentengine.messaging.InternalBufferProducer;
import mk.ukim.finki.sentimentengine.util.DtoTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * @author kristina
 */
@Component
public class GithubArchiveDataSource implements FileDataSource {

	private static final Logger logger = LoggerFactory.getLogger(GithubArchiveDataSource.class);
	private static final String SOURCE_NAME = "github-archive";
	private final ObjectMapper objectMapper;
	private final InternalBufferProducer bufferProducer;
	private volatile boolean running = false;

	public GithubArchiveDataSource(ObjectMapper objectMapper, InternalBufferProducer bufferProducer) {
		this.objectMapper = objectMapper;
		this.bufferProducer = bufferProducer;
	}

	@Override
	public int loadFile(Path filePath) {
		if (!running) {
			this.start();
		}
		long importStartTime = System.currentTimeMillis();
		logger.info("[DATA-IMPORT][GH-ARCHIVE] Loading from file: {}", filePath);
		int count = 0;
		int lineNumber = 0;

		try (BufferedReader reader = createReader(filePath)) {
			String line;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isBlank()) continue;

				try {
					GhArchiveEvent ghEvent = objectMapper.readValue(line, GhArchiveEvent.class);

					String eventType = ghEvent.type();
					Long timestamp = ghEvent.createdAt() != null ? ghEvent.createdAt().toEpochMilli() : null;
					String source = ghEvent.repo() != null ? ghEvent.repo().name() : null;

					if (timestamp == null) {
						logger.warn("[DATA-IMPORT][GH-ARCHIVE] Failed to parse timestamp, skipping event on line {}", lineNumber);
						continue;
					}

					EventDTO eventDTO = DtoTransformer.toEventDTO(eventType, timestamp, source, line);
					sendToBuffer(eventDTO);
					count++;
				} catch (Exception e) {
					logger.error("[DATA-IMPORT][GH-ARCHIVE] An error occurred while processing line {} in {}", lineNumber, filePath, e);
				}
				if (count % 100 == 0) {
					logger.info("[DATA-IMPORT][GH-ARCHIVE] Loaded {} events from {}..", count, SOURCE_NAME);
				}

			}

		} catch (IOException e) {
			logger.error("[DATA-IMPORT][GH-ARCHIVE] Failed to read file: {}", filePath, e);
		}
		logger.info("[DATA-IMPORT][GH-ARCHIVE] Finished loading {} events from {} in {} ms", count, filePath, System.currentTimeMillis() - importStartTime);
		return count;
	}


	@Override
	public int loadFiles(List<Path> filePaths) {
		if (!running) {
			start();
		}
		long batchImportStart = System.currentTimeMillis();
		logger.info("[DATA-IMPORT][GH-ARCHIVE] Starting batch loading of {} files ", filePaths.size());
		int totalCount = 0;

		for (Path filePath : filePaths) {
			totalCount += loadFile(filePath);
		}

		logger.info("[DATA-IMPORT][GH-ARCHIVE] Finished loading {} events from {} files in {} ms", totalCount,
			filePaths.size(), System.currentTimeMillis() - batchImportStart);
		return totalCount;
	}

	private BufferedReader createReader(Path filePath) throws IOException {
		InputStream inputStream = Files.newInputStream(filePath);
		if (filePath.toString().endsWith(".gz")) {
			inputStream = new GZIPInputStream(inputStream);
		}
		return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
	}


	@Override
	public void start() {
		this.running = true;
		logger.info("[DATA-IMPORT][] Starting {} ..", getName());
		// add importing here if you want automatic import and add it to UI
	}

	@Override
	public void stop() {
		this.running = false;
		logger.info("[DATA-IMPORT] Stopping {} ..", getName());
	}

	@Override
	public String getName() {
		return SOURCE_NAME;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public void sendToBuffer(EventDTO eventDTO) {
		bufferProducer.sendToBuffer(eventDTO);
	}
}
