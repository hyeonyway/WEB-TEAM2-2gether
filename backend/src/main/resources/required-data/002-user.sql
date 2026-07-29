-- Deterministic sellers and bidders for local service-flow data.

SET NAMES utf8mb4;
SET time_zone = '+09:00';
USE `dbidding`;
START TRANSACTION;

INSERT INTO `users`
  (`id`, `email`, `nickname`, `created_at`, `role`, `status`,
   `encrypted_password`, `salt`)
VALUES
  (1, 'debug-user@dbidding.local', '디버그사용자', NOW(6), 'USER', 'ACTIVE',
   SHA2('dbidding-debug-password', 256),
   LEFT(SHA2('dbidding-debug-salt', 256), 32))
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

COMMIT;
