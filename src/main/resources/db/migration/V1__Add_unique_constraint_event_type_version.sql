ALTER TABLE sentiment_rules
    ADD CONSTRAINT uq_sentiment_rules_event_type_version UNIQUE (event_type, version);
