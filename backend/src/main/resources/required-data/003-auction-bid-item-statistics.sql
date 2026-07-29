-- Service-flow seed data: auctions -> bids -> item statistics.
-- MySQL 8 recursive CTEs keep the source compact while producing a complete
-- 32 source days for every catalog item: a 30-day visible range plus
-- comparison baselines. Today is intentionally excluded from finalized stats.

SET NAMES utf8mb4;
SET time_zone = '+09:00';
USE `dbidding`;
START TRANSACTION;

-- Rebuild only the reserved card/date ranges so rerunning stays idempotent.
DELETE FROM `market_daily_statistics`
WHERE `statistics_date` BETWEEN CURDATE() - INTERVAL 31 DAY AND CURDATE() - INTERVAL 1 DAY;
DELETE FROM `item_daily_statistics`
WHERE `item_id` BETWEEN 1 AND 804
  AND `statistics_date` BETWEEN CURDATE() - INTERVAL 31 DAY AND CURDATE() - INTERVAL 1 DAY;
DELETE FROM `item_statistics`
WHERE `item_id` BETWEEN 1 AND 804;

DELETE FROM `bids`
WHERE `auction_id` BETWEEN 1000100 AND 1080431
   OR `auction_id` BETWEEN 2000001 AND 2000024;

DELETE FROM `auctions`
WHERE `id` BETWEEN 1000100 AND 1080431
   OR `id` BETWEEN 2000001 AND 2000024;

DROP TEMPORARY TABLE IF EXISTS `_seed_ended_auctions`;
CREATE TEMPORARY TABLE `_seed_ended_auctions` AS
WITH RECURSIVE `days` (`day_index`) AS (
  SELECT 0
  UNION ALL
  SELECT `day_index` + 1 FROM `days` WHERE `day_index` < 31
),
`prices` AS (
  SELECT
    `card`.`id` AS `item_id`,
    `days`.`day_index`,
    50000 + MOD(`card`.`id` * 7919, 450000) AS `base_price`,
    MOD(`card`.`id`, 5) AS `pattern_type`,
    1 + MOD(FLOOR(`card`.`id` / 5), 2) AS `amplitude`,
    CASE
      WHEN 50000 + MOD(`card`.`id` * 7919, 450000) >= 250000 THEN 5000
      ELSE 1000
    END AS `bid_unit`
  FROM `card_metadata` AS `card`
  CROSS JOIN `days`
  WHERE `card`.`id` BETWEEN 1 AND 804
),
`market_prices` AS (
  SELECT
    `prices`.*,
    (
      `base_price`
      + (`day_index` * (MOD(`item_id`, 3) - 1) * `bid_unit`)
      + (
        CASE `pattern_type`
          WHEN 0 THEN ELT(
            MOD(`day_index` + MOD((`item_id` * `item_id`) + (3 * `item_id`), 14), 14) + 1,
            -5, -4, -2, 1, 4, 7, 9, 8, 6, 3, 0, -2, -4, -5
          )
          WHEN 1 THEN ELT(
            MOD(`day_index` + MOD((`item_id` * 5) + 1, 9), 9) + 1,
            -3, 0, 3, 6, 7, 5, 2, -1, -3
          )
          WHEN 2 THEN ELT(
            MOD(`day_index` + MOD((`item_id` * 7) + 2, 11), 11) + 1,
            2, 5, 7, 6, 3, 0, -3, -5, -3, 0, 3
          )
          WHEN 3 THEN ELT(
            MOD(`day_index` + MOD((`item_id` * 11) + 3, 17), 17) + 1,
            -4, -2, 1, 4, 7, 8, 6, 3, 0, -3, -5, -6, -4, -1, 2, 4, 1
          )
          ELSE ELT(
            MOD(`day_index` + MOD((`item_id` * 13) + 4, 13), 13) + 1,
            0, 3, 5, 6, 4, 1, -2, -5, -6, -4, -1, 2, 1
          )
        END * `amplitude` * `bid_unit`
      )
    ) AS `winning_price`
  FROM `prices`
)
SELECT
  1000000 + (`item_id` * 100) + `day_index` AS `auction_id`,
  `item_id`,
  900001 + MOD(`item_id` - 1, 10) AS `seller_id`,
  `day_index`,
  `bid_unit`,
  LEAST(
    2
      + MOD(`item_id`, 3)
      + CASE MOD(`day_index`, 14)
          WHEN 0 THEN 0
          WHEN 1 THEN 1
          WHEN 2 THEN 3
          WHEN 3 THEN 5
          WHEN 4 THEN 4
          WHEN 5 THEN 2
          WHEN 6 THEN 1
          WHEN 7 THEN 0
          WHEN 8 THEN 2
          WHEN 9 THEN 4
          WHEN 10 THEN 6
          WHEN 11 THEN 3
          WHEN 12 THEN 1
          ELSE 0
        END,
    FLOOR(`winning_price` / `bid_unit`) - 1
  ) AS `number_of_bids`,
  `winning_price`
FROM `market_prices`;

ALTER TABLE `_seed_ended_auctions`
  ADD PRIMARY KEY (`auction_id`),
  ADD INDEX `idx_seed_ended_item_day` (`item_id`, `day_index`);

INSERT INTO `auctions`
  (`id`, `user_id`, `item_id`, `auction_name`, `description`,
   `start_price`, `current_price`, `buy_now_price`, `delivery_fee`,
   `status`, `open_time`, `estimated_close_time`, `close_time`,
   `bid_count`, `bid_price_unit`, `is_hyped`, `version`)
SELECT
  `seed`.`auction_id`,
  `seed`.`seller_id`,
  `seed`.`item_id`,
  CONCAT(`card`.`name`, ' 일일 경매'),
  CONCAT('최근 30일 서비스 흐름 초기 데이터 #', `seed`.`day_index` + 1),
  `seed`.`winning_price` - (`seed`.`bid_unit` * `seed`.`number_of_bids`),
  `seed`.`winning_price`,
  `seed`.`winning_price` + (`seed`.`bid_unit` * 10),
  3000,
  'ENDED',
  TIMESTAMPADD(
    HOUR,
    -8,
    TIMESTAMP(
      DATE_SUB(CURDATE(), INTERVAL (31 - `seed`.`day_index`) DAY),
      '00:00:00'
    )
  ),
  TIMESTAMP(
    DATE_SUB(CURDATE(), INTERVAL (31 - `seed`.`day_index`) DAY),
    '00:00:00'
  ),
  TIMESTAMP(
    DATE_SUB(CURDATE(), INTERVAL (31 - `seed`.`day_index`) DAY),
    '00:00:00'
  ),
  `seed`.`number_of_bids`,
  `seed`.`bid_unit`,
  MOD(`seed`.`item_id`, 8) = 0,
  1
FROM `_seed_ended_auctions` AS `seed`
JOIN `card_metadata` AS `card` ON `card`.`id` = `seed`.`item_id`
WHERE TRUE
ON DUPLICATE KEY UPDATE
  `user_id` = VALUES(`user_id`),
  `item_id` = VALUES(`item_id`),
  `auction_name` = VALUES(`auction_name`),
  `description` = VALUES(`description`),
  `start_price` = VALUES(`start_price`),
  `current_price` = VALUES(`current_price`),
  `buy_now_price` = VALUES(`buy_now_price`),
  `delivery_fee` = VALUES(`delivery_fee`),
  `status` = VALUES(`status`),
  `open_time` = VALUES(`open_time`),
  `estimated_close_time` = VALUES(`estimated_close_time`),
  `close_time` = VALUES(`close_time`),
  `bid_count` = VALUES(`bid_count`),
  `bid_price_unit` = VALUES(`bid_price_unit`),
  `is_hyped` = VALUES(`is_hyped`),
  `version` = VALUES(`version`);

INSERT INTO `bids`
  (`id`, `user_id`, `auction_id`, `bid_price`, `created_at`, `status`)
WITH RECURSIVE `bid_steps` (`step_number`) AS (
  SELECT 1
  UNION ALL
  SELECT `step_number` + 1 FROM `bid_steps` WHERE `step_number` < 10
)
SELECT
  (`seed`.`auction_id` * 10) + `steps`.`step_number`,
  900011 + MOD(
    `seed`.`item_id` + `seed`.`day_index` + `steps`.`step_number`,
    20
  ),
  `seed`.`auction_id`,
  `seed`.`winning_price`
    - ((`seed`.`number_of_bids` - `steps`.`step_number`) * `seed`.`bid_unit`),
  TIMESTAMPADD(
    MINUTE,
    -((`seed`.`number_of_bids` - `steps`.`step_number` + 1) * 30),
    TIMESTAMP(
      DATE_SUB(CURDATE(), INTERVAL (31 - `seed`.`day_index`) DAY),
      '00:00:00'
    )
  ),
  CASE
    WHEN `steps`.`step_number` = `seed`.`number_of_bids` THEN 'WON'
    ELSE 'LOST'
  END
FROM `_seed_ended_auctions` AS `seed`
CROSS JOIN `bid_steps` AS `steps`
WHERE `steps`.`step_number` <= `seed`.`number_of_bids`
ON DUPLICATE KEY UPDATE
  `user_id` = VALUES(`user_id`),
  `auction_id` = VALUES(`auction_id`),
  `bid_price` = VALUES(`bid_price`),
  `created_at` = VALUES(`created_at`),
  `status` = VALUES(`status`);

DROP TEMPORARY TABLE IF EXISTS `_seed_current_auctions`;
CREATE TEMPORARY TABLE `_seed_current_auctions` AS
WITH RECURSIVE `items` (`item_id`) AS (
  SELECT 1
  UNION ALL
  SELECT `item_id` + 1 FROM `items` WHERE `item_id` < 24
),
`current_values` AS (
  SELECT
    `items`.`item_id`,
    50000 + MOD(`items`.`item_id` * 7919, 450000) AS `base_price`,
    CASE
      WHEN 50000 + MOD(`items`.`item_id` * 7919, 450000) >= 250000 THEN 5000
      ELSE 1000
    END AS `bid_unit`,
    CASE
      WHEN MOD(`items`.`item_id`, 6) = 0 THEN 'SCHEDULED'
      WHEN MOD(`items`.`item_id`, 5) = 0 THEN 'ENDING'
      ELSE 'OPEN'
    END AS `auction_status`,
    CASE
      WHEN MOD(`items`.`item_id`, 6) = 0 OR MOD(`items`.`item_id`, 4) = 0 THEN 0
      ELSE 1 + MOD(`items`.`item_id`, 5)
    END AS `number_of_bids`
  FROM `items`
)
SELECT
  2000000 + `item_id` AS `auction_id`,
  `item_id`,
  900001 + MOD(`item_id` - 1, 10) AS `seller_id`,
  `base_price`,
  `bid_unit`,
  `auction_status`,
  `number_of_bids`,
  `base_price` + (`number_of_bids` * `bid_unit`) AS `current_price`
FROM `current_values`;

ALTER TABLE `_seed_current_auctions`
  ADD PRIMARY KEY (`auction_id`),
  ADD INDEX `idx_seed_current_item` (`item_id`);

INSERT INTO `auctions`
  (`id`, `user_id`, `item_id`, `auction_name`, `description`,
   `start_price`, `current_price`, `buy_now_price`, `delivery_fee`,
   `status`, `open_time`, `estimated_close_time`, `close_time`,
   `bid_count`, `bid_price_unit`, `is_hyped`, `version`)
SELECT
  `seed`.`auction_id`,
  `seed`.`seller_id`,
  `seed`.`item_id`,
  CONCAT(`card`.`name`, ' 실시간 경매'),
  '현재 진행 상태와 입찰 이력이 연결된 초기 데이터',
  `seed`.`base_price`,
  `seed`.`current_price`,
  `seed`.`base_price` + (`seed`.`bid_unit` * 15),
  3000,
  `seed`.`auction_status`,
  CASE
    WHEN `seed`.`auction_status` = 'SCHEDULED' THEN TIMESTAMPADD(HOUR, 2, NOW(6))
    ELSE TIMESTAMPADD(HOUR, -(2 + MOD(`seed`.`item_id`, 10)), NOW(6))
  END,
  CASE
    WHEN `seed`.`auction_status` = 'ENDING' THEN TIMESTAMPADD(MINUTE, 30, NOW(6))
    WHEN `seed`.`auction_status` = 'SCHEDULED' THEN TIMESTAMPADD(HOUR, 26, NOW(6))
    ELSE TIMESTAMPADD(HOUR, 3 + MOD(`seed`.`item_id`, 10), NOW(6))
  END,
  CASE
    WHEN `seed`.`auction_status` = 'ENDING' THEN TIMESTAMPADD(MINUTE, 30, NOW(6))
    WHEN `seed`.`auction_status` = 'SCHEDULED' THEN TIMESTAMPADD(HOUR, 26, NOW(6))
    ELSE TIMESTAMPADD(HOUR, 3 + MOD(`seed`.`item_id`, 10), NOW(6))
  END,
  `seed`.`number_of_bids`,
  `seed`.`bid_unit`,
  MOD(`seed`.`item_id`, 3) = 0,
  1
FROM `_seed_current_auctions` AS `seed`
JOIN `card_metadata` AS `card` ON `card`.`id` = `seed`.`item_id`
WHERE TRUE
ON DUPLICATE KEY UPDATE
  `user_id` = VALUES(`user_id`),
  `item_id` = VALUES(`item_id`),
  `auction_name` = VALUES(`auction_name`),
  `description` = VALUES(`description`),
  `start_price` = VALUES(`start_price`),
  `current_price` = VALUES(`current_price`),
  `buy_now_price` = VALUES(`buy_now_price`),
  `delivery_fee` = VALUES(`delivery_fee`),
  `status` = VALUES(`status`),
  `open_time` = VALUES(`open_time`),
  `estimated_close_time` = VALUES(`estimated_close_time`),
  `close_time` = VALUES(`close_time`),
  `bid_count` = VALUES(`bid_count`),
  `bid_price_unit` = VALUES(`bid_price_unit`),
  `is_hyped` = VALUES(`is_hyped`),
  `version` = VALUES(`version`);

INSERT INTO `bids`
  (`id`, `user_id`, `auction_id`, `bid_price`, `created_at`, `status`)
WITH RECURSIVE `bid_steps` (`step_number`) AS (
  SELECT 1
  UNION ALL
  SELECT `step_number` + 1 FROM `bid_steps` WHERE `step_number` < 5
)
SELECT
  (`seed`.`auction_id` * 10) + `steps`.`step_number`,
  900011 + MOD(`seed`.`item_id` + `steps`.`step_number`, 20),
  `seed`.`auction_id`,
  `seed`.`base_price` + (`steps`.`step_number` * `seed`.`bid_unit`),
  TIMESTAMPADD(
    MINUTE,
    -((`seed`.`number_of_bids` - `steps`.`step_number`) * 8),
    NOW(6)
  ),
  CASE
    WHEN `steps`.`step_number` = `seed`.`number_of_bids` THEN 'LEADING'
    ELSE 'OUTBID'
  END
FROM `_seed_current_auctions` AS `seed`
CROSS JOIN `bid_steps` AS `steps`
WHERE `steps`.`step_number` <= `seed`.`number_of_bids`
  AND `seed`.`auction_status` IN ('OPEN', 'ENDING')
ON DUPLICATE KEY UPDATE
  `user_id` = VALUES(`user_id`),
  `auction_id` = VALUES(`auction_id`),
  `bid_price` = VALUES(`bid_price`),
  `created_at` = VALUES(`created_at`),
  `status` = VALUES(`status`);

DROP TEMPORARY TABLE IF EXISTS `_seed_daily_statistics`;
CREATE TEMPORARY TABLE `_seed_daily_statistics` AS
SELECT
  `auction`.`item_id`,
  DATE(`auction`.`close_time`) AS `statistics_day`,
  SUBSTRING_INDEX(
    GROUP_CONCAT(
      `auction`.`current_price`
      ORDER BY `auction`.`close_time` DESC, `auction`.`id` DESC
    ),
    ',',
    1
  ) + 0 AS `latest_price`,
  ROUND(AVG(`auction`.`current_price`)) AS `avg_price`,
  MIN(`auction`.`current_price`) AS `lowest_price`,
  MAX(`auction`.`current_price`) AS `highest_price`,
  COUNT(`bid`.`id`) AS `bid_count`
FROM `auctions` AS `auction`
LEFT JOIN `bids` AS `bid` ON `bid`.`auction_id` = `auction`.`id`
WHERE `auction`.`id` BETWEEN 1000100 AND 1080431
  AND `auction`.`status` = 'ENDED'
GROUP BY `auction`.`item_id`, DATE(`auction`.`close_time`);

ALTER TABLE `_seed_daily_statistics`
  ADD PRIMARY KEY (`item_id`, `statistics_day`);

INSERT INTO `item_statistics`
  (`item_id`, `as_of_date`, `latest_price`, `average_price_30d`,
   `lowest_price_30d`, `highest_price_30d`, `bid_count_30d`,
   `ended_auction_count_30d`, `wishlist_count`,
   `daily_change_rate`, `weekly_change_rate`, `monthly_change_rate`)
SELECT
  `calculated`.`item_id`,
  CURDATE() - INTERVAL 1 DAY,
  `calculated`.`latest_price`,
  `calculated`.`rolling_average_price`,
  `calculated`.`rolling_lowest_price`,
  `calculated`.`rolling_highest_price`,
  `calculated`.`rolling_bid_count`,
  `calculated`.`rolling_ended_auction_count`,
  MOD(`calculated`.`item_id` * 13, 250),
  CASE
    WHEN `calculated`.`previous_daily_price` IS NULL
      OR `calculated`.`previous_daily_price` = 0 THEN 0.00
    ELSE ROUND(
      (`calculated`.`latest_price` - `calculated`.`previous_daily_price`)
      * 100.0 / `calculated`.`previous_daily_price`,
      2
    )
  END,
  CASE
    WHEN `calculated`.`previous_weekly_price` IS NULL
      OR `calculated`.`previous_weekly_price` = 0 THEN 0.00
    ELSE ROUND(
      (`calculated`.`latest_price` - `calculated`.`previous_weekly_price`)
      * 100.0 / `calculated`.`previous_weekly_price`,
      2
    )
  END,
  CASE
    WHEN `calculated`.`first_monthly_price` = 0 THEN 0.00
    ELSE ROUND(
      (`calculated`.`latest_price` - `calculated`.`first_monthly_price`)
      * 100.0 / `calculated`.`first_monthly_price`,
      2
    )
  END
FROM (
  SELECT
    `daily`.*,
    LAG(`daily`.`latest_price`, 1) OVER (
      PARTITION BY `daily`.`item_id` ORDER BY `daily`.`statistics_day`
    ) AS `previous_daily_price`,
    LAG(`daily`.`latest_price`, 7) OVER (
      PARTITION BY `daily`.`item_id` ORDER BY `daily`.`statistics_day`
    ) AS `previous_weekly_price`,
    FIRST_VALUE(`daily`.`latest_price`) OVER (
      PARTITION BY `daily`.`item_id`
      ORDER BY `daily`.`statistics_day`
      ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) AS `first_monthly_price`,
    MIN(`daily`.`lowest_price`) OVER (
      PARTITION BY `daily`.`item_id`
      ORDER BY `daily`.`statistics_day`
      ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
    ) AS `rolling_lowest_price`,
    MAX(`daily`.`highest_price`) OVER (
      PARTITION BY `daily`.`item_id`
      ORDER BY `daily`.`statistics_day`
      ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
    ) AS `rolling_highest_price`,
    ROUND(AVG(`daily`.`avg_price`) OVER (
      PARTITION BY `daily`.`item_id`
      ORDER BY `daily`.`statistics_day`
      ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
    )) AS `rolling_average_price`,
    SUM(`daily`.`bid_count`) OVER (
      PARTITION BY `daily`.`item_id`
      ORDER BY `daily`.`statistics_day`
      ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
    ) AS `rolling_bid_count`,
    COUNT(*) OVER (
      PARTITION BY `daily`.`item_id`
      ORDER BY `daily`.`statistics_day`
      ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
    ) AS `rolling_ended_auction_count`
  FROM `_seed_daily_statistics` AS `daily`
) AS `calculated`
WHERE `calculated`.`statistics_day` = CURDATE() - INTERVAL 1 DAY
ON DUPLICATE KEY UPDATE
  `as_of_date` = VALUES(`as_of_date`),
  `latest_price` = VALUES(`latest_price`),
  `average_price_30d` = VALUES(`average_price_30d`),
  `lowest_price_30d` = VALUES(`lowest_price_30d`),
  `highest_price_30d` = VALUES(`highest_price_30d`),
  `bid_count_30d` = VALUES(`bid_count_30d`),
  `ended_auction_count_30d` = VALUES(`ended_auction_count_30d`),
  `wishlist_count` = VALUES(`wishlist_count`),
  `daily_change_rate` = VALUES(`daily_change_rate`),
  `weekly_change_rate` = VALUES(`weekly_change_rate`),
  `monthly_change_rate` = VALUES(`monthly_change_rate`);

INSERT INTO `item_daily_statistics`
  (`item_id`, `statistics_date`, `latest_price`, `average_price`,
   `lowest_price`, `highest_price`, `bid_count`, `ended_auction_count`)
SELECT
  `item_id`, `statistics_day`, `latest_price`, `avg_price`,
  `lowest_price`, `highest_price`, `bid_count`, 1
FROM `_seed_daily_statistics`
WHERE `statistics_day` < CURDATE()
ON DUPLICATE KEY UPDATE
  `latest_price` = VALUES(`latest_price`),
  `average_price` = VALUES(`average_price`),
  `lowest_price` = VALUES(`lowest_price`),
  `highest_price` = VALUES(`highest_price`),
  `bid_count` = VALUES(`bid_count`),
  `ended_auction_count` = VALUES(`ended_auction_count`);

INSERT INTO `market_daily_statistics`
  (`statistics_date`, `average_price`, `lowest_price`, `highest_price`,
   `winning_price_total_30d`,
   `highest_price_30d`, `bid_count_30d`,
   `ended_auction_count_30d`,
   `bid_count`, `ended_auction_count`)
SELECT
  `daily`.`statistics_date`,
  `daily`.`average_price`,
  `daily`.`lowest_price`,
  `daily`.`highest_price`,
  SUM(`daily`.`daily_winning_price_total`) OVER (
    ORDER BY `daily`.`statistics_date`
    ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
  ),
  MAX(`daily`.`highest_price`) OVER (
    ORDER BY `daily`.`statistics_date`
    ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
  ),
  SUM(`daily`.`bid_count`) OVER (
    ORDER BY `daily`.`statistics_date`
    ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
  ),
  SUM(`daily`.`ended_auction_count`) OVER (
    ORDER BY `daily`.`statistics_date`
    ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
  ),
  `daily`.`bid_count`,
  `daily`.`ended_auction_count`
FROM (
  SELECT
    DATE(`auction`.`close_time`) AS `statistics_date`,
    ROUND(AVG(`auction`.`current_price`)) AS `average_price`,
    MIN(`auction`.`current_price`) AS `lowest_price`,
    MAX(`auction`.`current_price`) AS `highest_price`,
    SUM(`auction`.`current_price`) AS `daily_winning_price_total`,
    SUM((SELECT COUNT(*) FROM `bids` AS `daily_bid`
         WHERE `daily_bid`.`auction_id` = `auction`.`id`)) AS `bid_count`,
    COUNT(*) AS `ended_auction_count`
  FROM `auctions` AS `auction`
  WHERE `auction`.`id` BETWEEN 1000100 AND 1080431
    AND `auction`.`status` = 'ENDED'
    AND `auction`.`close_time` < CURDATE()
  GROUP BY DATE(`auction`.`close_time`)
) AS `daily`
ON DUPLICATE KEY UPDATE
  `average_price` = VALUES(`average_price`),
  `lowest_price` = VALUES(`lowest_price`),
  `highest_price` = VALUES(`highest_price`),
  `winning_price_total_30d` = VALUES(`winning_price_total_30d`),
  `highest_price_30d` = VALUES(`highest_price_30d`),
  `bid_count_30d` = VALUES(`bid_count_30d`),
  `ended_auction_count_30d` = VALUES(`ended_auction_count_30d`),
  `bid_count` = VALUES(`bid_count`),
  `ended_auction_count` = VALUES(`ended_auction_count`);

DROP TEMPORARY TABLE IF EXISTS `_seed_daily_statistics`;
DROP TEMPORARY TABLE IF EXISTS `_seed_current_auctions`;
DROP TEMPORARY TABLE IF EXISTS `_seed_ended_auctions`;

COMMIT;
