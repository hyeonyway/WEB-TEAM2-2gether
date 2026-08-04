-- Deterministic sellers and bidders for local service-flow data.

SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `dbidding`;
START TRANSACTION;

INSERT INTO `users`
  (`id`, `email`, `nickname`, `created_at`, `role`, `status`,
   `encrypted_password`, `salt`)
VALUES
  (1, 'dbidding@dbidding.com', '디비딩', NOW(6), 'USER', 'ACTIVE',
   '6bda87b448c683cc4790891f008a344146db8cf78420b56b16263b307f7a46b8',
   '6462696464696e672d757365722d3031')
ON DUPLICATE KEY UPDATE
  `email` = VALUES(`email`),
  `nickname` = VALUES(`nickname`),
  `role` = VALUES(`role`),
  `status` = VALUES(`status`),
  `encrypted_password` = VALUES(`encrypted_password`),
  `salt` = VALUES(`salt`);

INSERT INTO `users`
  (`id`, `email`, `nickname`, `created_at`, `role`, `status`,
   `encrypted_password`, `salt`)
WITH RECURSIVE `numbers` (`number`) AS (
  SELECT 1
  UNION ALL
  SELECT `number` + 1 FROM `numbers` WHERE `number` < 30
)
SELECT
  900000 + `number`,
  CONCAT(
    CASE WHEN `number` <= 10 THEN 'seller' ELSE 'bidder' END,
    LPAD(`number`, 2, '0'),
    '@dbidding.local'
  ),
  CONCAT(
    CASE WHEN `number` <= 10 THEN '판매자' ELSE '입찰자' END,
    LPAD(`number`, 2, '0')
  ),
  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL (60 + `number`) DAY), '09:00:00'),
  'USER',
  'ACTIVE',
  SHA2(CONCAT('dbidding-seed-password-', `number`), 256),
  LEFT(SHA2(CONCAT('dbidding-seed-salt-', `number`), 256), 32)
FROM `numbers`
WHERE TRUE
ON DUPLICATE KEY UPDATE
  `email` = VALUES(`email`),
  `nickname` = VALUES(`nickname`),
  `role` = VALUES(`role`),
  `status` = VALUES(`status`),
  `encrypted_password` = VALUES(`encrypted_password`),
  `salt` = VALUES(`salt`);

-- Give the primary local account a reproducible wallet balance.
INSERT INTO `wallets` (`user_id`, `point`)
VALUES (1, 5000000)
ON DUPLICATE KEY UPDATE
  `point` = VALUES(`point`);

-- Active auction fixtures use these deterministic users as leading bidders.
INSERT INTO `wallets` (`user_id`, `point`)
SELECT `id`, 5000000
FROM `users`
WHERE `id` BETWEEN 900001 AND 900030
ON DUPLICATE KEY UPDATE
  `point` = VALUES(`point`);

INSERT INTO `point_records`
  (`wallet_id`, `auction_id`, `amount`, `balance`,
   `transaction_type`, `idempotency_key`)
SELECT
  `wallet`.`id`,
  NULL,
  5000000,
  5000000,
  'CHARGE',
  'seed-user-1-initial-charge'
FROM `wallets` AS `wallet`
WHERE `wallet`.`user_id` = 1
ON DUPLICATE KEY UPDATE
  `auction_id` = VALUES(`auction_id`),
  `amount` = VALUES(`amount`),
  `balance` = VALUES(`balance`),
  `transaction_type` = VALUES(`transaction_type`);

INSERT INTO `point_records`
  (`wallet_id`, `auction_id`, `amount`, `balance`,
   `transaction_type`, `idempotency_key`)
SELECT
  `wallet`.`id`,
  NULL,
  5000000,
  5000000,
  'CHARGE',
  CONCAT('seed-user-', `wallet`.`user_id`, '-initial-charge')
FROM `wallets` AS `wallet`
WHERE `wallet`.`user_id` BETWEEN 900001 AND 900030
ON DUPLICATE KEY UPDATE
  `auction_id` = VALUES(`auction_id`),
  `amount` = VALUES(`amount`),
  `balance` = VALUES(`balance`),
  `transaction_type` = VALUES(`transaction_type`);

COMMIT;
