SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `dbidding`;

INSERT INTO `psa_certification_fixtures` (`certification_number`, `item_id`)
VALUES
  ('12345678', 1),
  ('23456789', 2),
  ('34567890', 8)
ON DUPLICATE KEY UPDATE `item_id` = VALUES(`item_id`);

UPDATE `card_metadata`
SET `issued_year` = CASE `id`
    WHEN 1 THEN '2024'
    WHEN 2 THEN '2023'
    WHEN 8 THEN '2025'
END,
    `card_number` = CASE `id`
    WHEN 1 THEN 'SV-P 001'
    WHEN 2 THEN 'SV-P 025'
    WHEN 8 THEN 'M3 079/100'
END
WHERE `id` IN (1, 2, 8);
