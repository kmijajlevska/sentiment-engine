package mk.ukim.finki.sentimentengine.api;

import jakarta.validation.Valid;
import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.data.dto.gharchive.GhArchiveEvent;
import mk.ukim.finki.sentimentengine.importer.AsyncExecutor;
import mk.ukim.finki.sentimentengine.importer.GithubArchiveDataSource;
import mk.ukim.finki.sentimentengine.messaging.InternalBufferProducer;
import mk.ukim.finki.sentimentengine.util.DataSourceTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * REST controller for importing data from various data sources.
 * Currently supports GitHub Archive file importer.
 *
 * @author kristina
 */
@RestController
@RequestMapping("loader")
public class LoaderController {

	private static final Logger log = LoggerFactory.getLogger(LoaderController.class);
	private final GithubArchiveDataSource githubArchiveDataSource;
	private final AsyncExecutor asyncExecutor;
	private final InternalBufferProducer bufferProducer;
	private final ObjectMapper objectMapper;
	@Value("${events.datasource.gh-archive.file.path}")
	private String ghArchiveFilePath;
	@Value("${events.datasource.gh-archive.directory.path}")
	private String ghArchiveDirectoryPath;

	public LoaderController(GithubArchiveDataSource githubArchiveDataSource,
	                        AsyncExecutor asyncExecutor,
	                        InternalBufferProducer bufferProducer,
	                        ObjectMapper objectMapper) {
		this.githubArchiveDataSource = githubArchiveDataSource;
		this.asyncExecutor = asyncExecutor;
		this.bufferProducer = bufferProducer;
		this.objectMapper = objectMapper;
	}

	@GetMapping("/github-archive/config")
	public ResponseEntity<Map<String, String>> getConfig() {
		return ResponseEntity.ok(Map.of(
			"filePath", ghArchiveFilePath,
			"directoryPath", ghArchiveDirectoryPath
		));
	}

	@PostMapping("/github-archive")
	public ResponseEntity<Map<String, String>> loadFile(@RequestParam(value = "path", required = false) String path) {
		String resolvedPath = (path != null && !path.isBlank()) ? path.trim() : ghArchiveFilePath;
		Path filePath = Path.of(resolvedPath);

		if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
			return ResponseEntity.badRequest().body(Map.of("error", "File does not exist or is not readable: " + resolvedPath));
		}

		asyncExecutor.loadFileAsync(githubArchiveDataSource, filePath);

		return ResponseEntity.accepted().body(Map.of("message", "Loading started for: " + filePath.getFileName().toString(),
			"dataSource", githubArchiveDataSource.getName()));
	}

	@PostMapping("/github-archive/batch")
	public ResponseEntity<Map<String, String>> loadBatch(@RequestParam(value = "path", required = false) String path) {
		try {
			String resolvedPath = (path != null && !path.isBlank()) ? path.trim() : ghArchiveDirectoryPath;
			Path dirPath = Path.of(resolvedPath);

			if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
				return ResponseEntity.badRequest().body(Map.of(
					"error", "Directory does not exist: " + resolvedPath));
			}

			List<Path> files = Files.list(dirPath).filter(f -> f.toString().endsWith(".json.gz") || f.toString().endsWith(".json"))
			                        .sorted()
			                        .toList();

			if (files.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("error", "No JSON files found in directory"));
			}

			asyncExecutor.loadBatchAsync(githubArchiveDataSource, files);

			return ResponseEntity.accepted().body(Map.of("message", "Batch loading started",
				"fileCount", String.valueOf(files.size())));
		} catch (Exception e) {
			log.error("[API][LOADER] Failed to load batch for GH Archive:  ", e);
			return ResponseEntity.internalServerError().body(Map.of("error", "Failed to load batch"));
		}
	}

	@PostMapping("/github-archive/event")
	public ResponseEntity<Map<String, String>> importEvent(@Valid @RequestBody GhArchiveEvent ghArchiveEvent) {
		try {
			String eventType = ghArchiveEvent.type();
			Long timestamp = ghArchiveEvent.createdAt().toEpochMilli();
			String source = ghArchiveEvent.repo().name();
			String payload = objectMapper.writeValueAsString(ghArchiveEvent);
			EventDTO eventDTO = DataSourceTransformer.generateEvent(eventType, timestamp, source, payload);
			bufferProducer.sendToBuffer(eventDTO);
			return ResponseEntity.accepted().body(Map.of("message", "Event imported successfully"));
		} catch (Exception e) {
			log.error("[API][LOADER] Failed to import event: ", e);
			return ResponseEntity.internalServerError().body(Map.of("error", "Failed to import event"));
		}
	}
}
