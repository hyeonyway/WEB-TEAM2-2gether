-- k6 부하테스트 전용 경매 300개.
-- 진행 경매의 2/3에 즉시낙찰가를 넣고, 모두 24시간 이내에 종료되도록 한다.
-- 원래 `seed-load-test-auctions.js`로 POST /api/auctions API를 통해
-- 런타임에 만들었는데, DB 스키마 리셋(reset-on-mismatch) 때마다 날아가서
-- required-data로 옮겼다. ID 3001001~3001300 대역은 다른 required-data
-- 파일(3000001~3000112, 3000301~3000305)과 겹치지 않게 분리했다.

SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `dbidding`;
START TRANSACTION;

DELETE FROM `wallet_holds`
WHERE `auction_id` BETWEEN 3001001 AND 3001300;

DELETE FROM `bids`
WHERE `auction_id` BETWEEN 3001001 AND 3001300;

DELETE FROM `images`
WHERE `auction_id` BETWEEN 3001001 AND 3001300;

DELETE FROM `auctions`
WHERE `id` BETWEEN 3001001 AND 3001300;

DROP TEMPORARY TABLE IF EXISTS `_seed_k6_load_test_auctions`;
CREATE TEMPORARY TABLE `_seed_k6_load_test_auctions` AS
WITH RECURSIVE `items` (`item_id`) AS (
  SELECT 1
  UNION ALL
  SELECT `item_id` + 1
  FROM `items`
  WHERE `item_id` < 300
)
SELECT
  3001000 + `item_id` AS `auction_id`,
  `item_id`,
  900001 + MOD(`item_id` - 1, 10) AS `seller_id`,
  10000 + MOD(`item_id` * 7919, 50000) AS `start_price`
FROM `items`;

ALTER TABLE `_seed_k6_load_test_auctions`
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
  CONCAT(`card`.`name`, ' k6 부하테스트 경매'),
  '부하테스트 전용 경매입니다.',
  `seed`.`start_price`,
  `seed`.`start_price`,
  CASE
    WHEN MOD(`seed`.`item_id`, 3) <> 0
      THEN `seed`.`start_price` + 30000
    ELSE NULL
  END,
  3000,
  'OPEN',
  NOW(6),
  TIMESTAMPADD(HOUR, 24, NOW(6)),
  TIMESTAMPADD(HOUR, 24, NOW(6)),
  0,
  1000,
  0
FROM `_seed_k6_load_test_auctions` AS `seed`
JOIN `card_metadata` AS `card` ON `card`.`id` = `seed`.`item_id`;

INSERT INTO `images` (`auction_id`, `image_path`)
SELECT
  `seed`.`auction_id`,
  `card`.`image_path`
FROM `_seed_k6_load_test_auctions` AS `seed`
JOIN `card_metadata` AS `card` ON `card`.`id` = `seed`.`item_id`;

DROP TEMPORARY TABLE IF EXISTS `_seed_k6_load_test_auctions`;

COMMIT;
