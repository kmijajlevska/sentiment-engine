package mk.ukim.finki.sentimentengine.importer;

import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.messaging.InternalBufferProducer;
import mk.ukim.finki.sentimentengine.util.DataSourceTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
		logger.info("[DATA-IMPORT] Loading from file: {}", filePath);
		int count = 0;
		int lineNumber = 0;

		try (BufferedReader reader = createReader(filePath)) {
			String line;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isBlank()) continue;

				try {
					// ATM full model parsing is not necessary
					JsonNode node = objectMapper.readTree(line);
					String eventType = parseEventType(node, lineNumber);
					Long timestamp = parseTimestamp(node, lineNumber);
					String source = parseSource(node, lineNumber);
					//  add user if necessary

					EventDTO eventDTO = DataSourceTransformer.generateEvent(eventType, timestamp, source, line);
					bufferProducer.sendToBuffer(eventDTO);
					count++;
					logger.info("[DATA-IMPORT] Loaded {} events from {}..", count, SOURCE_NAME);
				} catch (Exception e) {
					logger.error("[DATA-IMPORT] An error occurred while processing line {} in {}", lineNumber, filePath, e);
				}

			}

		} catch (IOException e) {
			logger.error("[DATA-IMPORT] Failed to read file: {}", filePath, e);
		}
		logger.info("[DATA-IMPORT] Finished loading {} events from {} in {} ms", count, filePath, System.currentTimeMillis() - importStartTime);
		return count;
	}


	private String parseSource(JsonNode node, int lineNumber) {
		if (node == null) {
			logger.warn("[DATA-IMPORT] Failed to parse source, node is null on line: {}", lineNumber);
			return null;
		}
		if (node.has("repo") && node.get("repo").has("name")) {
			return node.get("repo").get("name").asString();
		}
		return null;
	}

	private Long parseTimestamp(JsonNode node, int lineNumber) {
		if (node == null) {
			logger.warn("[DATA-IMPORT] Failed to parse timestamp, node is null on line: {}", lineNumber);
			return null;
		}

		if (node.has("created_at")) {
			String raw = node.get("created_at").asString();
			try {
				return Instant.parse(raw).toEpochMilli();
			} catch (Exception e) {
				logger.warn("[DATA-IMPORT] Failed to parse timestamp on line: {}", lineNumber);
			}
		}
		return null; // should we process events without timestamp at all ?
	}


	private String parseEventType(JsonNode node, int lineNumber) {
		if (node == null) {
			logger.warn("[DATA-IMPORT] Failed to parse eventType, node is null on line: {}", lineNumber);
			return null;
		}
		return node.has("type") ? node.get("type").asString() : null;
	}


	@Override
	public int loadFiles(List<Path> filePaths) {
		if (!running) {
			start();
		}
		long batchImportStart = System.currentTimeMillis();
		logger.info("[DATA-IMPORT] Starting batch loading of {} files ", filePaths.size());
		int totalCount = 0;

		for (Path filePath : filePaths) {
			totalCount += loadFile(filePath);
		}

		logger.info("[DATA-IMPORT] Finished loading {} events from {} files in {} ms", totalCount,
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
		logger.info("[DATA-IMPORT] Starting {} ..", getName());
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
}
