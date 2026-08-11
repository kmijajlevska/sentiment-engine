package mk.ukim.finki.sentimentengine.data.entity;

/**
 * Value object holding the outcome of a sentiment evaluation.
 *
 * @param score      sentiment score in [-1.0, 1.0]
 * @param ruleId     ID of the rule that was applied (null if none)
 * @param confidence confidence level in [0.0, 1.0]
 *
 * @author kristina
 */
public record SentimentResult(double score, Long ruleId, double confidence) {}
