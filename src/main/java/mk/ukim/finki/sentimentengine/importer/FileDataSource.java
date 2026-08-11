package mk.ukim.finki.sentimentengine.importer;

import java.nio.file.Path;
import java.util.List;

/**
 * Extension of EventDataSource for file-based data sources.
 * Adds methods specific to loading data from files.
 *
 * @author kristina
 */
public interface FileDataSource extends EventDataSource {

	int loadFile(Path filePath);

	int loadFiles(List<Path> filePaths);
}