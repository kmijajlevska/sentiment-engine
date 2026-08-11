package mk.ukim.finki.sentimentengine.api;

import mk.ukim.finki.sentimentengine.importer.AsyncExecutor;
import mk.ukim.finki.sentimentengine.importer.GithubArchiveDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * REST controller for importing data dump from various data sources.
 * Currently supports GitHub Archive file importer
 *
 * @author kristina
 */
@RestController
@RequestMapping("loader")
public class LoaderController {

	private static final Logger log = LoggerFactory.getLogger(LoaderController.class);
	private final GithubArchiveDataSource githubArchiveDataSource;
	private final AsyncExecutor asyncExecutor;
	@Value("${events.datasource.gh-archive.file.path}")
	private String ghArchiveFilePath;
	@Value("${events.datasource.gh-archive.directory.path}")
	private String ghArchiveDirectoryPath;

	public LoaderController(GithubArchiveDataSource githubArchiveDataSource,
	                        AsyncExecutor asyncExecutor) {
		this.githubArchiveDataSource = githubArchiveDataSource;
		this.asyncExecutor = asyncExecutor;
	}

	@PostMapping("/github-archive")
	public ResponseEntity<Map<String, String>> loadFile() {
		Path filePath = Path.of(ghArchiveFilePath);

		if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
			return ResponseEntity.badRequest().body(Map.of(
				"error", "File does not exist or is not readable: " + ghArchiveFilePath
			));
		}

		asyncExecutor.loadFileAsync(githubArchiveDataSource, filePath);

		return ResponseEntity.accepted().body(Map.of(
			"message", "Loading started for: " + filePath.getFileName().toString(),
			"dataSource", githubArchiveDataSource.getName()
		));
	}

	@PostMapping("/github-archive/batch")
	public ResponseEntity<Map<String, String>> loadBatch() {
		try {
			Path dirPath = Path.of(ghArchiveDirectoryPath);
			if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
				return ResponseEntity.badRequest().body(Map.of(
					"error", "Directory does not exist: " + ghArchiveDirectoryPath));
			}

			List<Path> files = Files.list(dirPath)
			                        .filter(f -> f.toString().endsWith(".json.gz") || f.toString().endsWith(".json"))
			                        .sorted()
			                        .toList();

			if (files.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("error", "No JSON files found in directory"));
			}

			asyncExecutor.loadBatchAsync(githubArchiveDataSource, files);

			return ResponseEntity.accepted().body(Map.of(
				"message", "Batch loading started",
				"fileCount", String.valueOf(files.size())
			));
		} catch (Exception e) {
			log.error("[API][LOADER] Failed to load batch for GH Archive:  ", e);
			return ResponseEntity.internalServerError().body(Map.of("error", " Failed to load batch "));
		}
	}
}