ALTER TABLE import_batch ADD COLUMN preview_mapping jsonb;
ALTER TABLE import_batch ADD COLUMN preview_by text;
CREATE INDEX import_history ON import_batch(created_at DESC);
