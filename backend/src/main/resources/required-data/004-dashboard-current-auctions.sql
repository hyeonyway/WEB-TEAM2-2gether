-- Dashboard seed data for DEBUG_USER_ID=1.
-- Creates 50 auctions in progress and 12 recently won auctions.
-- Every auction has bids from deterministic seed users.

SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `dbidding`;
START TRANSACTION;

DELETE FROM `wallet_holds`
WHERE `auction_id` BETWEEN 3000001 AND 3000050;

DELETE FROM `bids`
WHERE `auction_id` BETWEEN 3000001 AND 3000050;

DELETE FROM `images`
WHERE `auction_id` BETWEEN 3000001 AND 3000050;

DELETE FROM `auctions`
WHERE `id` BETWEEN 3000001 AND 3000050;

DELETE FROM `wallet_holds`
WHERE `auction_id` BETWEEN 3000101 AND 3000112;

DELETE FROM `bids`
WHERE `auction_id` BETWEEN 3000101 AND 3000112;

DELETE FROM `images`
WHERE `auction_id` BETWEEN 3000101 AND 3000112;

DELETE FROM `auctions`
WHERE `id` BETWEEN 3000101 AND 3000112;

DROP TEMPORARY TABLE IF EXISTS `_seed_dashboard_auctions`;
CREATE TEMPORARY TABLE `_seed_dashboard_auctions` AS
WITH RECURSIVE `items` (`item_id`) AS (
  SELECT 1
  UNION ALL
  SELECT `item_id` + 1
  FROM `items`
  WHERE `item_id` < 50
)
SELECT
  3000000 + `item_id` AS `auction_id`,
  `item_id`,
  900001 + MOD(`item_id` - 1, 10) AS `seller_id`,
  50000 + MOD(`item_id` * 7919, 300000) AS `start_price`,
  CASE
    WHEN 50000 + MOD(`item_id` * 7919, 300000) >= 250000 THEN 5000
    ELSE 1000
  END AS `bid_unit`,
  2 + MOD(`item_id`, 4) AS `number_of_bids`,
  CASE
    WHEN MOD(`item_id`, 5) = 0 THEN 'ENDING'
    ELSE 'OPEN'
  END AS `auction_status`
FROM `items`;

ALTER TABLE `_seed_dashboard_auctions`
  ADD PRIMARY KEY (`auction_id`);

INSERT INTO `auctions`
  (`id`, `user_id`, `item_id`, `auction_name`, `description`,
   `start_price`, `current_price`, `buy_now_price`, `delivery_fee`,
   `status`, `open_time`, `estimated_close_time`, `close_time`,
   `bid_count`, `bid_price_unit`, `is_hyped`)
SELECT
  `seed`.`auction_id`,
  `seed`.`seller_id`,
  `seed`.`item_id`,
  CONCAT(`card`.`name`, ' 오늘의 경매'),
  '오늘 시작한 대시보드 테스트용 진행 경매',
  `seed`.`start_price`,
  `seed`.`start_price` + (`seed`.`number_of_bids` * `seed`.`bid_unit`),
  -- 진행 경매의 절반은 즉시 구매를 지원한다.
  CASE
    WHEN MOD(`seed`.`item_id`, 2) = 0
      THEN `seed`.`start_price` + ((`seed`.`number_of_bids` + 10) * `seed`.`bid_unit`)
    ELSE NULL
  END,
  3000,
  `seed`.`auction_status`,
  TIMESTAMP(CURDATE(), '00:00:00'),
  CASE
    WHEN `seed`.`auction_status` = 'ENDING'
      THEN TIMESTAMPADD(MINUTE, 20 + MOD(`seed`.`item_id`, 40), NOW(6))
    ELSE TIMESTAMPADD(DAY, 7, NOW(6))
  END,
  CASE
    WHEN `seed`.`auction_status` = 'ENDING'
      THEN TIMESTAMPADD(MINUTE, 20 + MOD(`seed`.`item_id`, 40), NOW(6))
    ELSE TIMESTAMPADD(DAY, 7, NOW(6))
  END,
  `seed`.`number_of_bids`,
  `seed`.`bid_unit`,
  MOD(`seed`.`item_id`, 4) = 0
FROM `_seed_dashboard_auctions` AS `seed`
JOIN `card_metadata` AS `card` ON `card`.`id` = `seed`.`item_id`;

INSERT INTO `images` (`auction_id`, `image_path`)
SELECT
  `seed`.`auction_id`,
  `card`.`image_path`
FROM `_seed_dashboard_auctions` AS `seed`
JOIN `card_metadata` AS `card` ON `card`.`id` = `seed`.`item_id`;

INSERT INTO `bids`
  (`id`, `user_id`, `auction_id`, `bid_price`, `created_at`, `status`)
WITH RECURSIVE `bid_steps` (`step_number`) AS (
  SELECT 1
  UNION ALL
  SELECT `step_number` + 1
  FROM `bid_steps`
  WHERE `step_number` < 5
)
SELECT
  (`seed`.`auction_id` * 10) + `steps`.`step_number`,
  CASE
    -- Debug user 1 is the current leader in one third of the auctions.
    WHEN MOD(`seed`.`item_id`, 3) = 0
      AND `steps`.`step_number` = `seed`.`number_of_bids`
      THEN 1
    -- Debug user 1 participated but was outbid in another third.
    WHEN MOD(`seed`.`item_id`, 3) = 1
      AND `steps`.`step_number` = 1
      THEN 1
    -- Remaining bid history belongs to other deterministic users.
    ELSE 900012 + MOD(
      (`seed`.`item_id` * 3) + `steps`.`step_number`,
      19
    )
  END,
  `seed`.`auction_id`,
  `seed`.`start_price` + (`steps`.`step_number` * `seed`.`bid_unit`),
  TIMESTAMPADD(
    MINUTE,
    -(
      (`seed`.`number_of_bids` - `steps`.`step_number`) * 7
      + MOD(`seed`.`item_id`, 6)
    ),
    NOW(6)
  ),
  CASE
    WHEN `steps`.`step_number` = `seed`.`number_of_bids` THEN 'LEADING'
    ELSE 'OUTBID'
  END
FROM `_seed_dashboard_auctions` AS `seed`
CROSS JOIN `bid_steps` AS `steps`
WHERE `steps`.`step_number` <= `seed`.`number_of_bids`;

INSERT INTO `wallet_holds`
  (`wallet_id`, `auction_id`, `amount`, `status`, `created_at`)
SELECT
  `wallet`.`id`,
  `bid`.`auction_id`,
  `bid`.`bid_price`,
  'HELD',
  `bid`.`created_at`
FROM `bids` AS `bid`
JOIN `auctions` AS `auction` ON `auction`.`id` = `bid`.`auction_id`
JOIN `wallets` AS `wallet` ON `wallet`.`user_id` = `bid`.`user_id`
WHERE `bid`.`auction_id` BETWEEN 3000001 AND 3000050
  AND `bid`.`status` = 'LEADING'
  AND `auction`.`status` IN ('OPEN', 'ENDING');

DROP TEMPORARY TABLE IF EXISTS `_seed_dashboard_auctions`;

DROP TEMPORARY TABLE IF EXISTS `_seed_dashboard_wins`;
CREATE TEMPORARY TABLE `_seed_dashboard_wins` AS
WITH RECURSIVE `items` (`sequence`) AS (
  SELECT 1
  UNION ALL
  SELECT `sequence` + 1
  FROM `items`
  WHERE `sequence` < 12
)
SELECT
  3000100 + `sequence` AS `auction_id`,
  50 + `sequence` AS `item_id`,
  900001 + MOD(`sequence` - 1, 10) AS `seller_id`,
  70000 + MOD(`sequence` * 13271, 280000) AS `start_price`,
  CASE
    WHEN 70000 + MOD(`sequence` * 13271, 280000) >= 250000 THEN 5000
    ELSE 1000
  END AS `bid_unit`,
  3 + MOD(`sequence`, 3) AS `number_of_bids`,
  `sequence`
FROM `items`;

ALTER TABLE `_seed_dashboard_wins`
  ADD PRIMARY KEY (`auction_id`);

INSERT INTO `auctions`
  (`id`, `user_id`, `item_id`, `auction_name`, `description`,
   `start_price`, `current_price`, `buy_now_price`, `delivery_fee`,
   `status`, `open_time`, `estimated_close_time`, `close_time`,
   `bid_count`, `bid_price_unit`, `is_hyped`)
SELECT
  `seed`.`auction_id`,
  `seed`.`seller_id`,
  `seed`.`item_id`,
  CONCAT(`card`.`name`, ' 낙찰 완료 경매'),
  '사용자 1의 최근 낙찰 대시보드 테스트용 종료 경매',
  `seed`.`start_price`,
  `seed`.`start_price` + (`seed`.`number_of_bids` * `seed`.`bid_unit`),
  `seed`.`start_price` + (12 * `seed`.`bid_unit`),
  3000,
  'ENDED',
  TIMESTAMPADD(DAY, -(`seed`.`sequence` + 2), NOW(6)),
  TIMESTAMPADD(DAY, -`seed`.`sequence`, NOW(6)),
  TIMESTAMPADD(DAY, -`seed`.`sequence`, NOW(6)),
  `seed`.`number_of_bids`,
  `seed`.`bid_unit`,
  MOD(`seed`.`sequence`, 3) = 0
FROM `_seed_dashboard_wins` AS `seed`
JOIN `card_metadata` AS `card` ON `card`.`id` = `seed`.`item_id`;

INSERT INTO `images` (`auction_id`, `image_path`)
SELECT
  `seed`.`auction_id`,
  `card`.`image_path`
FROM `_seed_dashboard_wins` AS `seed`
JOIN `card_metadata` AS `card` ON `card`.`id` = `seed`.`item_id`;

INSERT INTO `bids`
  (`id`, `user_id`, `auction_id`, `bid_price`, `created_at`, `status`)
WITH RECURSIVE `bid_steps` (`step_number`) AS (
  SELECT 1
  UNION ALL
  SELECT `step_number` + 1
  FROM `bid_steps`
  WHERE `step_number` < 5
)
SELECT
  (`seed`.`auction_id` * 10) + `steps`.`step_number`,
  CASE
    WHEN `steps`.`step_number` = `seed`.`number_of_bids` THEN 1
    ELSE 900012 + MOD(
      (`seed`.`sequence` * 3) + `steps`.`step_number`,
      19
    )
  END,
  `seed`.`auction_id`,
  `seed`.`start_price` + (`steps`.`step_number` * `seed`.`bid_unit`),
  TIMESTAMPADD(
    MINUTE,
    -(
      ((`seed`.`number_of_bids` - `steps`.`step_number`) * 8) + 1
    ),
    TIMESTAMPADD(DAY, -`seed`.`sequence`, NOW(6))
  ),
  CASE
    WHEN `steps`.`step_number` = `seed`.`number_of_bids` THEN 'WON'
    ELSE 'OUTBID'
  END
FROM `_seed_dashboard_wins` AS `seed`
CROSS JOIN `bid_steps` AS `steps`
WHERE `steps`.`step_number` <= `seed`.`number_of_bids`;

DROP TEMPORARY TABLE IF EXISTS `_seed_dashboard_wins`;

COMMIT;
