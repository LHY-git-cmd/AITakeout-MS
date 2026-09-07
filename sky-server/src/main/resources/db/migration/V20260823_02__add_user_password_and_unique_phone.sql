ALTER TABLE user
    ADD COLUMN password varchar(100) NULL AFTER phone;

UPDATE user
SET password = '$2b$12$MBHd1HhwPooitSKPsj1L4e.aJ392u/cPAv6ZYcAWcmJy1FDAZSRmu'
WHERE phone IS NOT NULL AND password IS NULL;

CREATE UNIQUE INDEX uk_user_phone ON user (phone);
