-- Identifies the exact cooldown owner so failed sends cannot release a newer reservation.
ALTER TABLE `auth_sms_cooldown`
    ADD COLUMN `reservation_id` VARCHAR(64) NOT NULL DEFAULT '' AFTER `purpose`;
