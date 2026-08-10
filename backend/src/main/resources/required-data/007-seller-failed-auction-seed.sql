-- Failed (유찰) auction seed data for DEBUG_USER_ID=1.
-- Covers the "내가 판 주문 → 유찰" dashboard tab (issue #233): auctions that
-- closed with no winning bid never get an Order row, so they need their own
-- seed distinct from 006's ENDED/order seed. User 1 is the seller on both,
-- with zero bids each, matching the real "no LEADING/WON bid" definition of
-- AuctionStatus.FAILED.

SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `dbidding`;
START TRANSACTION;

DELETE FROM `bids` WHERE `auction_id` BETWEEN 3000304 AND 3000305;
DELETE FROM `images` WHERE `auction_id` BETWEEN 3000304 AND 3000305;
DELETE FROM `auctions` WHERE `id` BETWEEN 3000304 AND 3000305;

INSERT INTO `auctions`
  (`id`, `user_id`, `item_id`, `auction_name`, `description`,
   `start_price`, `current_price`, `buy_now_price`, `delivery_fee`,
   `status`, `open_time`, `estimated_close_time`, `close_time`,
   `bid_count`, `bid_price_unit`, `is_hyped`)
VALUES
  (3000304, 1, 9, '피카츄 P 스칼렛&바이올렛 프로모 카드 경매', '유찰 시드 #1',
   15000, 15000, 40000, 3000,
   'FAILED', NOW(6) - INTERVAL 4 DAY, NOW(6) - INTERVAL 3 DAY, NOW(6) - INTERVAL 3 DAY,
   0, 1000, FALSE),
  (3000305, 1, 10, '나테오 브이스타 AR 포켓몬 카드 경매', '유찰 시드 #2',
   20000, 20000, 45000, 3000,
   'FAILED', NOW(6) - INTERVAL 2 DAY, NOW(6) - INTERVAL 1 DAY, NOW(6) - INTERVAL 1 DAY,
   0, 1000, FALSE);

INSERT INTO `images` (`auction_id`, `image_path`)
SELECT `auctions`.`id`, `card_metadata`.`image_path`
FROM `auctions`
JOIN `card_metadata` ON `card_metadata`.`id` = `auctions`.`item_id`
WHERE `auctions`.`id` BETWEEN 3000304 AND 3000305;

COMMIT;
