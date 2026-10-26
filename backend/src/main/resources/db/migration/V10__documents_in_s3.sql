-- Documents can live in S3 (app.documents.s3.bucket): the row keeps the metadata and the object key, the bucket the
-- encrypted bytes. Without a bucket (local development, tests) they stay in content.
ALTER TABLE stored_documents ALTER COLUMN content DROP NOT NULL;
ALTER TABLE stored_documents ADD COLUMN storage_key VARCHAR(200);
ALTER TABLE stored_documents ADD CONSTRAINT stored_documents_location_chk
    CHECK ((content IS NULL) <> (storage_key IS NULL));
