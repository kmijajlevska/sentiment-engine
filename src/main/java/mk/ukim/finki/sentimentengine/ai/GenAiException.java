package mk.ukim.finki.sentimentengine.ai;

/**
 * @author kristina
 */
public class GenAiException extends RuntimeException {
	public GenAiException(String message) {
		super(message);
	}

	public GenAiException(String message, Throwable cause) {
		super(message, cause);
	}
}
