CREATE TABLE raw_events
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    timestamp  BIGINT       NOT NULL,
    source     VARCHAR(256),
    payload    JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX      idx_raw_events_event_type (event_type),
    INDEX      idx_raw_events_timestamp (timestamp)
);

CREATE TABLE event_types
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                  VARCHAR(128),
    first_seen_at         BIGINT  NOT NULL,
    last_seen_at          BIGINT  NOT NULL,
    occurrence_count      BIGINT  NOT NULL DEFAULT 0,
    sample_payload_schema JSON,
    has_rule              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sentiment_rules
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type      VARCHAR(128) NOT NULL,
    rule_type       VARCHAR(32)  NOT NULL,
    rule_definition JSON,
    base_score DOUBLE NOT NULL DEFAULT 0.0,
    explanation     TEXT,
    version         INT          NOT NULL DEFAULT 1,
    created_at      TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    INDEX           idx_sentiment_rules_event_type (event_type)
);

CREATE TABLE processed_events
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id        BIGINT       NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    event_timestamp BIGINT       NOT NULL,
    sentiment_score DOUBLE NOT NULL DEFAULT 0.0,
    confidence DOUBLE,
    applied_rule_id BIGINT,
    minute_bucket   BIGINT       NOT NULL,
    hour_bucket     BIGINT       NOT NULL,
    day_bucket      DATE         NOT NULL,
    week_bucket     DATE         NOT NULL,
    month_bucket    DATE         NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX           idx_processed_event_type (event_type),
    INDEX           idx_processed_minute_bucket (minute_bucket),
    INDEX           idx_processed_hour_bucket (hour_bucket),
    INDEX           idx_processed_day_bucket (day_bucket),
    INDEX           idx_processed_week_bucket (week_bucket),
    INDEX           idx_processed_month_bucket (month_bucket)
);