-- Home dashboard demo data.
-- Dates are generated from CURDATE() so the 30-day chart and previous-day
-- rankings remain visible whenever the database is initialized.

SET NAMES utf8mb4;
SET time_zone = '+09:00';
USE `dbidding`;
START TRANSACTION;

INSERT INTO `card_sets` (`id`, `name`, `code`)
VALUES (900001, 'Home Dashboard Demo Set', 'HOME-DEMO')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`);

INSERT INTO `card_metadata`
  (`id`, `card_set_id`, `name`, `language`, `psa_grade`, `rarity`, `image_path`)
VALUES
  (900001, 900001, '피카츄 P 메가 에볼루션 프로모카드', 'Japanese', 'PSA 10', 'P',
   'pokemon-cards/card_f75f026b35a79452e2a2.webp'),
  (900002, 900001, '피카츄 P 스칼렛&바이올렛 프로모 카드', 'Japanese', 'PSA 10', 'P',
   'pokemon-cards/card_24032acf332f8defafa6.webp'),
  (900003, 900001, '피카츄 프로모 카드', 'Japanese', 'PSA 10', 'P',
   'pokemon-cards/card_974e2008747e339a471f.webp'),
  (900004, 900001, '메타몽의 타임 캡슐 프로모 카드', 'Japanese', 'PSA 10', 'AR',
   'pokemon-cards/card_4f5e83915f21654d8ebf.webp'),
  (900005, 900001, '피카츄 25주년 기념 컬렉션', 'Japanese', 'PSA 10', 'P',
   'pokemon-cards/card_db483cdd5c2134071d77.webp'),
  (900006, 900001, 'PSA 10 프리미엄 카드', 'Japanese', 'PSA 10', 'SAR',
   'pokemon-cards/card_b2995524785841f2366b.webp'),
  (900007, 900001, '고라파덕 AR 카드', 'Japanese', 'PSA 10', 'AR',
   'pokemon-cards/card_a69feb93246faae1fae6.webp'),
  (900008, 900001, '리자몽 스페셜 아트 카드', 'Japanese', 'PSA 10', 'SAR',
   'pokemon-cards/card_c471b35230d34fd914d8.webp')
ON DUPLICATE KEY UPDATE
  `card_set_id` = VALUES(`card_set_id`),
  `name` = VALUES(`name`),
  `language` = VALUES(`language`),
  `psa_grade` = VALUES(`psa_grade`),
  `rarity` = VALUES(`rarity`),
  `image_path` = VALUES(`image_path`);

INSERT INTO `users`
  (`id`, `email`, `nickname`, `created_at`, `role`, `status`, `encrypted_password`, `salt`)
VALUES
  (900001, 'home-demo@dbidding.local', '홈데모판매자', NOW(6), 'USER', 'ACTIVE',
   REPEAT('0', 64), REPEAT('0', 32))
ON DUPLICATE KEY UPDATE
  `nickname` = VALUES(`nickname`),
  `status` = 'ACTIVE';

-- Current auctions used by all three insight cards.
INSERT INTO `auctions`
  (`id`, `user_id`, `item_id`, `auction_name`, `description`,
   `start_price`, `current_price`, `buy_now_price`, `delivery_fee`,
   `status`, `open_time`, `estimated_close_time`, `close_time`,
   `bid_count`, `bid_price_unit`, `is_hyped`, `version`)
VALUES
  (900001, 900001, 900001, '피카츄 메가 에볼루션 프로모 경매', '홈 인사이트 데모 경매',
   90000, 138000, 180000, 3000, 'OPEN', NOW(6) - INTERVAL 8 HOUR,
   NOW(6) + INTERVAL 4 HOUR, NOW(6) + INTERVAL 4 HOUR, 36, 1000, TRUE, 1),
  (900002, 900001, 900002, '피카츄 스칼렛&바이올렛 프로모 경매', '홈 인사이트 데모 경매',
   100000, 135000, 180000, 3000, 'ENDING', NOW(6) - INTERVAL 10 HOUR,
   NOW(6) + INTERVAL 40 MINUTE, NOW(6) + INTERVAL 40 MINUTE, 31, 1000, TRUE, 1),
  (900003, 900001, 900003, '피카츄 프로모 카드 경매', '홈 인사이트 데모 경매',
   300000, 415000, 500000, 3000, 'OPEN', NOW(6) - INTERVAL 6 HOUR,
   NOW(6) + INTERVAL 6 HOUR, NOW(6) + INTERVAL 6 HOUR, 27, 5000, TRUE, 1),
  (900004, 900001, 900004, '메타몽 타임 캡슐 프로모 경매', '홈 인사이트 데모 경매',
   140000, 171000, 220000, 3000, 'OPEN', NOW(6) - INTERVAL 5 HOUR,
   NOW(6) + INTERVAL 8 HOUR, NOW(6) + INTERVAL 8 HOUR, 19, 1000, FALSE, 1),
  (900005, 900001, 900005, '피카츄 25주년 컬렉션 경매', '홈 인사이트 데모 경매',
   95000, 115000, 160000, 3000, 'ENDING', NOW(6) - INTERVAL 12 HOUR,
   NOW(6) + INTERVAL 20 MINUTE, NOW(6) + INTERVAL 20 MINUTE, 16, 1000, FALSE, 1),
  (900006, 900001, 900006, 'PSA 10 프리미엄 카드 경매', '홈 인사이트 데모 경매',
   500000, 571000, 700000, 3000, 'OPEN', NOW(6) - INTERVAL 4 HOUR,
   NOW(6) + INTERVAL 10 HOUR, NOW(6) + INTERVAL 10 HOUR, 14, 5000, TRUE, 1),
  (900007, 900001, 900007, '고라파덕 AR 카드 경매', '홈 인사이트 데모 경매',
   157000, 157000, 210000, 3000, 'OPEN', NOW(6) - INTERVAL 3 HOUR,
   NOW(6) + INTERVAL 12 HOUR, NOW(6) + INTERVAL 12 HOUR, 0, 1000, FALSE, 1),
  (900008, 900001, 900008, '리자몽 스페셜 아트 경매', '홈 인사이트 데모 경매',
   250000, 289000, 380000, 3000, 'OPEN', NOW(6) - INTERVAL 7 HOUR,
   NOW(6) + INTERVAL 5 HOUR, NOW(6) + INTERVAL 5 HOUR, 22, 5000, TRUE, 1)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`),
  `open_time` = VALUES(`open_time`),
  `estimated_close_time` = VALUES(`estimated_close_time`),
  `close_time` = VALUES(`close_time`),
  `current_price` = VALUES(`current_price`),
  `bid_count` = VALUES(`bid_count`);

-- One completed auction per day: day 0 is the baseline immediately before
-- the 30-day range and days 1..30 populate every chart point.
INSERT INTO `auctions`
  (`id`, `user_id`, `item_id`, `auction_name`, `description`,
   `start_price`, `current_price`, `buy_now_price`, `delivery_fee`,
   `status`, `open_time`, `estimated_close_time`, `close_time`,
   `bid_count`, `bid_price_unit`, `is_hyped`, `version`)
WITH RECURSIVE `days` (`day_number`) AS (
  SELECT 0
  UNION ALL
  SELECT `day_number` + 1 FROM `days` WHERE `day_number` < 30
)
SELECT
  900100 + `day_number`,
  900001,
  900001 + MOD(`day_number`, 8),
  CONCAT('홈 차트 종료 경매 ', LPAD(`day_number`, 2, '0')),
  '최근 30일 홈 차트 데모 경매',
  110000 + (`day_number` * 2500),
  140000 + (`day_number` * 3500) + (MOD(`day_number`, 5) * 7000),
  300000 + (`day_number` * 4000),
  3000,
  'ENDED',
  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL (31 - `day_number`) DAY), '09:00:00'),
  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL (30 - `day_number`) DAY), '12:00:00'),
  TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL (30 - `day_number`) DAY), '12:00:00'),
  55 + MOD(`day_number` * 17, 150),
  1000,
  FALSE,
  1
FROM `days`
ON DUPLICATE KEY UPDATE
  `status` = 'ENDED',
  `open_time` = VALUES(`open_time`),
  `estimated_close_time` = VALUES(`estimated_close_time`),
  `close_time` = VALUES(`close_time`),
  `current_price` = VALUES(`current_price`),
  `bid_count` = VALUES(`bid_count`);

-- Snapshots immediately before yesterday and today make the previous-day
-- TOP5 deterministic while retaining the real card metadata and artwork.
INSERT INTO `item_statistics`
  (`item_id`, `statistics_date`, `latest_price`, `avg_price`,
   `lowest_price`, `highest_price`, `bid_count`, `active_auction_count`,
   `wishlist_count`, `daily_change_rate`, `weekly_change_rate`, `monthly_change_rate`)
VALUES
  (900001, CURDATE() - INTERVAL 2 DAY, 120000, 120000, 110000, 128000, 29, 1, 42, 0.00, 4.00, 8.00),
  (900002, CURDATE() - INTERVAL 2 DAY, 120000, 120000, 112000, 129000, 25, 1, 35, 0.00, 3.00, 7.00),
  (900003, CURDATE() - INTERVAL 2 DAY, 380000, 380000, 350000, 400000, 21, 1, 31, 0.00, 2.00, 6.00),
  (900004, CURDATE() - INTERVAL 2 DAY, 160000, 160000, 148000, 168000, 16, 1, 24, 0.00, 2.00, 5.00),
  (900005, CURDATE() - INTERVAL 2 DAY, 110000, 110000, 101000, 118000, 13, 1, 22, 0.00, 1.00, 4.00),
  (900006, CURDATE() - INTERVAL 2 DAY, 580000, 580000, 550000, 610000, 12, 1, 20, 0.00, 1.00, 3.00),
  (900007, CURDATE() - INTERVAL 2 DAY, 160000, 160000, 152000, 169000, 0, 1, 18, 0.00, 1.00, 3.00),
  (900008, CURDATE() - INTERVAL 2 DAY, 280000, 280000, 265000, 295000, 18, 1, 28, 0.00, 2.00, 5.00),
  (900001, CURDATE() - INTERVAL 1 DAY, 138000, 138000, 126000, 149000, 36, 1, 48, 15.00, 7.00, 12.00),
  (900002, CURDATE() - INTERVAL 1 DAY, 135000, 135000, 124000, 145000, 31, 1, 41, 12.50, 6.00, 11.00),
  (900003, CURDATE() - INTERVAL 1 DAY, 415000, 415000, 390000, 438000, 27, 1, 38, 9.21, 5.00, 10.00),
  (900004, CURDATE() - INTERVAL 1 DAY, 171000, 171000, 159000, 182000, 19, 1, 29, 6.88, 4.00, 8.00),
  (900005, CURDATE() - INTERVAL 1 DAY, 115000, 115000, 106000, 123000, 16, 1, 26, 4.55, 3.00, 7.00),
  (900006, CURDATE() - INTERVAL 1 DAY, 571000, 571000, 545000, 600000, 14, 1, 23, -1.55, 0.00, 2.00),
  (900007, CURDATE() - INTERVAL 1 DAY, 157000, 157000, 149000, 166000, 0, 1, 20, -1.88, 0.00, 2.00),
  (900008, CURDATE() - INTERVAL 1 DAY, 289000, 289000, 272000, 305000, 22, 1, 33, 3.21, 3.00, 7.00)
ON DUPLICATE KEY UPDATE
  `latest_price` = VALUES(`latest_price`),
  `avg_price` = VALUES(`avg_price`),
  `lowest_price` = VALUES(`lowest_price`),
  `highest_price` = VALUES(`highest_price`),
  `bid_count` = VALUES(`bid_count`),
  `active_auction_count` = VALUES(`active_auction_count`),
  `wishlist_count` = VALUES(`wishlist_count`),
  `daily_change_rate` = VALUES(`daily_change_rate`),
  `weekly_change_rate` = VALUES(`weekly_change_rate`),
  `monthly_change_rate` = VALUES(`monthly_change_rate`);

COMMIT;
