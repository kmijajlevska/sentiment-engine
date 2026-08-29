package mk.ukim.finki.sentimentengine.api;

import mk.ukim.finki.sentimentengine.data.dto.EventDTO;
import mk.ukim.finki.sentimentengine.importer.AsyncExecutor;
import mk.ukim.finki.sentimentengine.importer.GithubArchiveDataSource;
import mk.ukim.finki.sentimentengine.messaging.InternalBufferProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link LoaderController} using the MVC slice.
 *
 * <p>Maps to user scenario 4.1 (Data Import): the three GitHub Archive import modes — single file,
 * batch directory, and a single pasted event. File/directory imports respond immediately with 202
 * Accepted because processing happens asynchronously in the background; a single pasted event is
 * transformed and pushed onto the internal buffer.
 */
@WebMvcTest(LoaderController.class)
@TestPropertySource(properties = {
	"events.datasource.gh-archive.file.path=/tmp/does-not-exist-default.json",
	"events.datasource.gh-archive.directory.path=/tmp/does-not-exist-default-dir"
})
class LoaderControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GithubArchiveDataSource githubArchiveDataSource;
	@MockitoBean
	private AsyncExecutor asyncExecutor;
	@MockitoBean
	private InternalBufferProducer bufferProducer;

	@Test
	@DisplayName("POST /loader/github-archive accepts a readable file and starts async loading")
	void loadFileAccepted(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("events.json");
		Files.writeString(file, "{}\n");
		when(githubArchiveDataSource.getName()).thenReturn("github-archive");

		mockMvc.perform(post("/loader/github-archive").param("path", file.toString()))
		       .andExpect(status().isAccepted())
		       .andExpect(jsonPath("$.message").exists());

		verify(asyncExecutor).loadFileAsync(eq(githubArchiveDataSource), eq(file));
	}

	@Test
	@DisplayName("POST /loader/github-archive rejects a non-existent file path")
	void loadFileRejectsMissingPath() throws Exception {
		mockMvc.perform(post("/loader/github-archive").param("path", "/no/such/file.json"))
		       .andExpect(status().isBadRequest());

		verify(asyncExecutor, never()).loadFileAsync(any(), any());
	}

	@Test
	@DisplayName("POST /loader/github-archive/batch starts async batch loading for a directory of files")
	void loadBatchAccepted(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("a.json"), "{}\n");
		Files.writeString(tmp.resolve("b.json"), "{}\n");
		when(githubArchiveDataSource.getName()).thenReturn("github-archive");

		mockMvc.perform(post("/loader/github-archive/batch").param("path", tmp.toString()))
		       .andExpect(status().isAccepted())
		       .andExpect(jsonPath("$.fileCount").value("2"));

		verify(asyncExecutor).loadBatchAsync(eq(githubArchiveDataSource), anyList());
	}

	@Test
	@DisplayName("POST /loader/github-archive/batch rejects a missing directory")
	void loadBatchRejectsMissingDirectory() throws Exception {
		mockMvc.perform(post("/loader/github-archive/batch").param("path", "/no/such/dir"))
		       .andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /loader/github-archive/event transforms a single event and pushes it to the buffer")
	void importSingleEvent() throws Exception {
		String body = """
			{
			  "id": "123",
			  "type": "PushEvent",
			  "public": true,
			  "repo": {"id": "1", "name": "octocat/hello-world", "url": "https://api.github.com/repos/octocat/hello-world"},
			  "created_at": "2024-01-01 12:00:00 UTC"
			}
			""";

		mockMvc.perform(post("/loader/github-archive/event")
			       .contentType(MediaType.APPLICATION_JSON)
			       .content(body))
		       .andExpect(status().isAccepted())
		       .andExpect(jsonPath("$.message").exists());

		verify(bufferProducer).sendToBuffer(any(EventDTO.class));
	}
}
