UPDATE environments SET token = 'REVOKED' WHERE token IS NULL;
ALTER TABLE environments ALTER COLUMN token SET NOT NULL;
