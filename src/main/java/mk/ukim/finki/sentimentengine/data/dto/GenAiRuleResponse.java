package mk.ukim.finki.sentimentengine.data.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing the structured response from GenAI for sentiment rule generation.
 *
 * @author kristina
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenAiRuleResponse {

	private double baseScore;
	private List<String> keywords;
	private String explanation;
}
