ALTER TABLE event_types
    ADD CONSTRAINT uq_event_types_name UNIQUE (name);
