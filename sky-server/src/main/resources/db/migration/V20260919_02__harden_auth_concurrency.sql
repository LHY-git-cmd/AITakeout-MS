-- Atomically serializes SMS cooldown reservations across application instances.
CREATE TABLE `auth_sms_cooldown` (
    `phone` VARCHAR(32) NOT NULL,
    `purpose` VARCHAR(32) NOT NULL,
    `next_allowed_at` DATETIME NOT NULL,
    PRIMARY KEY (`phone`, `purpose`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送冷却原子闸门';
