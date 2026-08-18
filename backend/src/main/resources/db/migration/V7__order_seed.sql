-- Source: backend/src/main/resources/required-data/006-order-seed.sql
-- Order seed data for DEBUG_USER_ID=1.
-- Covers both sides of the "주문" dashboard tab:
--   * 내가 산 주문 (buyer=1) — reuses 4 of user 1's already-won auctions from
--     004 (3000104-3000107) that don't have an order yet.
--   * 내가 판 주문 (seller=1) — user 1 has never sold anything in existing
--     seed data, so this creates 3 new ended auctions (3000301-3000303) with
--     user 1 as seller and a winning bid from another seed buyer.
-- Both sides include a PENDING_CONFIRM row so the confirm/cancel buttons have
-- something to act on locally.

SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `dbidding`;
START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 내가 판 주문: brand-new auctions where user 1 is the seller.
-- ---------------------------------------------------------------------------

DELETE FROM `orders` WHERE `auction_id` BETWEEN 3000301 AND 3000303;
DELETE FROM `bids` WHERE `auction_id` BETWEEN 3000301 AND 3000303;
DELETE FROM `images` WHERE `auction_id` BETWEEN 3000301 AND 3000303;
DELETE FROM `auctions` WHERE `id` BETWEEN 3000301 AND 3000303;

INSERT INTO `auctions`
  (`id`, `user_id`, `item_id`, `auction_name`, `description`,
   `start_price`, `current_price`, `buy_now_price`, `delivery_fee`,
   `status`, `open_time`, `estimated_close_time`, `close_time`,
   `bid_count`, `bid_price_unit`, `is_hyped`)
VALUES
  (3000301, 1, 6, '피카츄 P 유나가바 소드&실드 프로모 카드 경매', '판매 주문 시드 #1',
   50000, 68000, 90000, 3000,
   'ENDED', NOW(6) - INTERVAL 3 DAY, NOW(6) - INTERVAL 2 DAY, NOW(6) - INTERVAL 2 DAY,
   3, 3000, FALSE),
  (3000302, 1, 7, '피카츄 P 스칼렛&바이올렛 프로모 카드 경매', '판매 주문 시드 #2',
   40000, 55000, 80000, 3000,
   'ENDED', NOW(6) - INTERVAL 2 DAY, NOW(6) - INTERVAL 1 DAY, NOW(6) - INTERVAL 1 DAY,
   4, 3000, FALSE),
  (3000303, 1, 8, '고라파덕 AR 메가 드림 ex 경매', '판매 주문 시드 #3',
   30000, 42000, 60000, 3000,
   'ENDED', NOW(6) - INTERVAL 1 DAY, NOW(6), NOW(6),
   2, 2000, FALSE);

INSERT INTO `images` (`auction_id`, `image_path`)
SELECT `auctions`.`id`, `card_metadata`.`image_path`
FROM `auctions`
JOIN `card_metadata` ON `card_metadata`.`id` = `auctions`.`item_id`
WHERE `auctions`.`id` BETWEEN 3000301 AND 3000303;

INSERT INTO `bids` (`user_id`, `auction_id`, `bid_price`, `created_at`, `status`)
VALUES
  (900001, 3000301, 68000, NOW(6) - INTERVAL 2 DAY, 'WON'),
  (900002, 3000302, 55000, NOW(6) - INTERVAL 1 DAY, 'WON'),
  (900003, 3000303, 42000, NOW(6), 'WON');

INSERT INTO `orders` (`auction_id`, `buyer_id`, `seller_id`, `card_name`, `price`, `status`)
SELECT `auctions`.`id`, `bids`.`user_id`, `auctions`.`user_id`, `card_metadata`.`name`,
       `bids`.`bid_price`,
       CASE `auctions`.`id`
         WHEN 3000301 THEN 'PENDING_CONFIRM'
         WHEN 3000302 THEN 'COMPLETED'
         WHEN 3000303 THEN 'CANCELLED'
       END
FROM `auctions`
JOIN `bids` ON `bids`.`auction_id` = `auctions`.`id` AND `bids`.`status` = 'WON'
JOIN `card_metadata` ON `card_metadata`.`id` = `auctions`.`item_id`
WHERE `auctions`.`id` BETWEEN 3000301 AND 3000303
ON DUPLICATE KEY UPDATE
  `card_name` = VALUES(`card_name`),
  `price` = VALUES(`price`),
  `status` = VALUES(`status`);

-- ---------------------------------------------------------------------------
-- 내가 산 주문: reuse 4 of user 1's already-won auctions from 004 that don't
-- have an order row yet (3000101-3000103 already got one from earlier manual
-- testing; this covers a few more with a PENDING_CONFIRM mix).
-- ---------------------------------------------------------------------------

DELETE FROM `orders` WHERE `auction_id` IN (3000104, 3000105, 3000106, 3000107);

INSERT INTO `orders` (`auction_id`, `buyer_id`, `seller_id`, `card_name`, `price`, `status`)
SELECT `auctions`.`id`, `bids`.`user_id`, `auctions`.`user_id`, `card_metadata`.`name`,
       `bids`.`bid_price`,
       CASE `auctions`.`id`
         WHEN 3000104 THEN 'PENDING_CONFIRM'
         WHEN 3000105 THEN 'PENDING_CONFIRM'
         WHEN 3000106 THEN 'COMPLETED'
         WHEN 3000107 THEN 'CANCELLED'
       END
FROM `auctions`
JOIN `bids` ON `bids`.`auction_id` = `auctions`.`id` AND `bids`.`status` = 'WON'
JOIN `card_metadata` ON `card_metadata`.`id` = `auctions`.`item_id`
WHERE `auctions`.`id` IN (3000104, 3000105, 3000106, 3000107)
ON DUPLICATE KEY UPDATE
  `card_name` = VALUES(`card_name`),
  `price` = VALUES(`price`),
  `status` = VALUES(`status`);

COMMIT;
