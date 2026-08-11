package mk.ukim.finki.sentimentengine.data.dto.gharchive;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;

/**
 * POJO representing a single event from GitHub Archive (GH Archive).
 * All events in the dataset share this exact structure.
 *
 * @author kristina
 */
public record GhArchiveEvent(
	String id,
	String type,
	@JsonProperty("public") boolean publicEvent,
	String payload,
	GhRepo repo,
	GhActor actor,
	GhOrg org,
	@JsonProperty("created_at")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss z", timezone = "UTC")
	Instant createdAt,
	String other
) implements Serializable {

	public record GhRepo(
		String id,
		String name,
		String url
	) implements Serializable {}

	public record GhActor(
		String id,
		String login,
		@JsonProperty("gravatar_id") String gravatarId,
		@JsonProperty("avatar_url") String avatarUrl,
		String url
	) implements Serializable {}

	public record GhOrg(
		String id,
		String login,
		@JsonProperty("gravatar_id") String gravatarId,
		@JsonProperty("avatar_url") String avatarUrl,
		String url
	) implements Serializable {}
}