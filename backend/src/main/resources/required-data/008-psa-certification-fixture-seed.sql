SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `dbidding`;

INSERT INTO `psa_certification_fixtures` (`certification_number`, `item_id`)
VALUES
  ('12345678', 1),
  ('23456789', 2),
  ('34567890', 8)
ON DUPLICATE KEY UPDATE `item_id` = VALUES(`item_id`);
