-- Run before Hibernate updates the schema. Fresh installations use the entity defaults.
ALTER TABLE IF EXISTS indexing_job_outbox ALTER COLUMN image_url DROP NOT NULL;
ALTER TABLE IF EXISTS ocr_search_outbox ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 1099511627776;
