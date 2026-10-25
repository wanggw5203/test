-- Administrator installs this in a dedicated test database. Runtime has SELECT/INSERT/UPDATE only.
-- H2 verified. Validate timestamp/unique constraints and driver semantics for your production DB.
CREATE TABLE testkit_resource_lease (
    resource_key VARCHAR(160) PRIMARY KEY,
    owner_token VARCHAR(64),
    expires_at TIMESTAMP NOT NULL,
    fence BIGINT NOT NULL
);
