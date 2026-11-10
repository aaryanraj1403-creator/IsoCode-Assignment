CREATE INDEX order_import ON order_record(import_id) WHERE import_id IS NOT NULL;
CREATE INDEX movement_import ON movement(import_id) WHERE import_id IS NOT NULL;
