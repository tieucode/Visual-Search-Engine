-- Run before Hibernate updates the schema. Fresh installations use the entity defaults.
ALTER TABLE IF EXISTS indexing_job_outbox ALTER COLUMN image_url DROP NOT NULL;
ALTER TABLE IF EXISTS ocr_search_outbox ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 1099511627776;
ALTER TABLE IF EXISTS search_clicks ALTER COLUMN search_history_id DROP NOT NULL;
ALTER TABLE IF EXISTS images ADD COLUMN IF NOT EXISTS media_kind varchar(10) NOT NULL DEFAULT 'IMAGE';
ALTER TABLE IF EXISTS images ADD COLUMN IF NOT EXISTS duration_ms integer;
-- Hibernate does not update existing enum check constraints when a value is added.
DO 'BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint
        WHERE conrelid = to_regclass(''images'') AND conname = ''images_file_format_check''
          AND pg_get_constraintdef(oid) NOT LIKE ''%MP4%'') THEN
        ALTER TABLE images DROP CONSTRAINT images_file_format_check;
        ALTER TABLE images ADD CONSTRAINT images_file_format_check
            CHECK (file_format IN (''JPEG'', ''JPG'', ''PNG'', ''WEBP'', ''GIF'', ''BMP'', ''MP4''));
    END IF;
END';
