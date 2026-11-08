-- Convert a notifications table created by current schema.sql back to the
-- pre-V2.6.2 V2.6.0 shape used by legacy migration tests.
--
-- This fixture is intentionally destructive and must only be applied to
-- borrowbox_legacy_test after schema.sql has been loaded.

ALTER TABLE notifications DROP FOREIGN KEY fk_notifications_source_event;
ALTER TABLE notifications DROP INDEX uq_notifications_recipient_source_event_type;
ALTER TABLE notifications DROP INDEX uq_notifications_recipient_dedupe;
ALTER TABLE notifications DROP INDEX idx_notifications_source_event;
ALTER TABLE notifications DROP COLUMN source_event_id;
ALTER TABLE notifications DROP COLUMN dedupe_key;

-- The legacy deployment carried this obsolete uniqueness rule. It allowed
-- duplicates whenever transaction_id was NULL, which is why V2.6.2 replaces it.
ALTER TABLE notifications
    ADD CONSTRAINT uq_notifications_user_type_transaction
    UNIQUE (recipient_id, type, transaction_id);
