ALTER TABLE processed_events
    MODIFY COLUMN confidence DECIMAL(3, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE processed_events
    MODIFY COLUMN sentiment_score DECIMAL(3, 2) NOT NULL DEFAULT 0.00;
