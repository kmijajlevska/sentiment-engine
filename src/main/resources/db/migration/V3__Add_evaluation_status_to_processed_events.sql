ALTER TABLE processed_events
    ADD COLUMN evaluation_status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED';

CREATE INDEX idx_processed_evaluation_status_event_type
    ON processed_events (evaluation_status, event_type);
