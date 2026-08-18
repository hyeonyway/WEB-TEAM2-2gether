-- Source: backend/src/main/resources/required-data/002-user.sql
-- Deterministic sellers and bidders for local service-flow data.

SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `dbidding`;
START TRANSACTION;

-- id=1 원래 비밀번호는 기록이 안 남아있어 100회 기준으로 재계산할 수 없었다
-- (docs/hyeonmoon/auth/6-password-hash-cost-tuning.md 참고). 데모/k6 환경
-- (PASSWORD_HASH_ITERATIONS=100)에서 로그인 가능하도록 알려진 비밀번호로
-- 재설정했다.
-- Password: Dbidding123!
-- The hash below is PBKDF2WithHmacSHA256 (100 iterations, 256 bits; demo only),
-- matching PasswordHasher. Only valid when PASSWORD_HASH_ITERATIONS=100.
INSERT INTO `users`
  (`id`, `email`, `nickname`, `created_at`, `role`, `status`,
   `encrypted_password`, `salt`)
VALUES
  (1, 'dbidding@dbidding.com', '디비딩', NOW(6), 'USER', 'ACTIVE',
   '180cd1dc8f210c0407949401ebd334b6a95b24dd5d4dcda1643487be90cf9d6f',
   '6462696464696e672d757365722d3031')
ON DUPLICATE KEY UPDATE
  `email` = VALUES(`email`),
  `nickname` = VALUES(`nickname`),
  `role` = VALUES(`role`),
  `status` = VALUES(`status`),
  `encrypted_password` = VALUES(`encrypted_password`),
  `salt` = VALUES(`salt`);

-- Local administrator for the Stream recovery console.
-- Credentials: admin@dbidding.com / Dbidding123!
-- Uses the same demo-only PBKDF2 parameters as the primary local account.
INSERT INTO `users`
  (`id`, `email`, `nickname`, `created_at`, `role`, `status`,
   `encrypted_password`, `salt`)
VALUES
  (2, 'admin@dbidding.com', '관리자', NOW(6), 'ADMIN', 'ACTIVE',
   '180cd1dc8f210c0407949401ebd334b6a95b24dd5d4dcda1643487be90cf9d6f',
   '6462696464696e672d757365722d3031')
ON DUPLICATE KEY UPDATE
  `email` = VALUES(`email`),
  `nickname` = VALUES(`nickname`),
  `role` = VALUES(`role`),
  `status` = VALUES(`status`),
  `encrypted_password` = VALUES(`encrypted_password`),
  `salt` = VALUES(`salt`);

-- k6 bid load-test accounts.
-- Credentials: k6-user00001@dbidding.local .. k6-user50000@dbidding.local
-- Password (all accounts): K6LoadTest123!
-- The hash below is PBKDF2WithHmacSHA256 (100 iterations, 256 bits; demo only),
-- matching PasswordHasher. MySQL SHA2 is not compatible with application login.
SET SESSION cte_max_recursion_depth = 50000;
INSERT INTO `users`
  (`id`, `email`, `nickname`, `created_at`, `role`, `status`,
   `encrypted_password`, `salt`)
WITH RECURSIVE `numbers` (`number`) AS (
  SELECT 1
  UNION ALL
  SELECT `number` + 1 FROM `numbers` WHERE `number` < 50000
)
SELECT
  910000 + `number`,
  CONCAT('k6-user', LPAD(`number`, 5, '0'), '@dbidding.local'),
  CONCAT('부하테스트', LPAD(`number`, 5, '0')),
  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '09:00:00'),
  'USER',
  'ACTIVE',
  '9bf31158e6621e360af2186721ceb7337300ae425e0bfd587042165af6ec6ce7',
  '6b362d6c6f61642d746573742d73616c'
FROM `numbers`
WHERE TRUE
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

-- Give the primary local account and local administrator reproducible wallet balances.
INSERT INTO `wallets` (`user_id`, `point`)
VALUES (1, 5000000)
ON DUPLICATE KEY UPDATE
  `point` = VALUES(`point`);

INSERT INTO `wallets` (`user_id`, `point`)
VALUES (2, 50000000)
ON DUPLICATE KEY UPDATE
  `point` = VALUES(`point`);

-- Active auction fixtures use these deterministic users as leading bidders.
INSERT INTO `wallets` (`user_id`, `point`)
SELECT `id`, 5000000
FROM `users`
WHERE `id` BETWEEN 900001 AND 900030
ON DUPLICATE KEY UPDATE
  `point` = VALUES(`point`);

INSERT INTO `wallets` (`user_id`, `point`)
SELECT `id`, 50000000
FROM `users`
WHERE `id` BETWEEN 910001 AND 960000
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
  50000000,
  50000000,
  'CHARGE',
  'seed-admin-initial-charge'
FROM `wallets` AS `wallet`
WHERE `wallet`.`user_id` = 2
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

INSERT INTO `point_records`
  (`wallet_id`, `auction_id`, `amount`, `balance`,
   `transaction_type`, `idempotency_key`)
SELECT
  `wallet`.`id`,
  NULL,
  50000000,
  50000000,
  'CHARGE',
  CONCAT('seed-k6-user-', `wallet`.`user_id`, '-initial-charge')
FROM `wallets` AS `wallet`
WHERE `wallet`.`user_id` BETWEEN 910001 AND 960000
ON DUPLICATE KEY UPDATE
  `auction_id` = VALUES(`auction_id`),
  `amount` = VALUES(`amount`),
  `balance` = VALUES(`balance`),
  `transaction_type` = VALUES(`transaction_type`);

COMMIT;
