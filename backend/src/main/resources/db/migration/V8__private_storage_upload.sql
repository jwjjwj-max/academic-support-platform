CREATE TABLE storage_upload (
 id VARCHAR(36) PRIMARY KEY,
 owner_id BIGINT NOT NULL,
 object_key VARCHAR(255) NOT NULL UNIQUE,
 request_json TEXT NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 status VARCHAR(20) NOT NULL,
 result_json MEDIUMTEXT NULL,
 INDEX idx_storage_upload_expiry (expires_at)
);
