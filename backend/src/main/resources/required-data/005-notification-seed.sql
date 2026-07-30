-- Notification seed data for DEBUG_USER_ID=1.
-- Reuses the auctions created by 004-dashboard-current-auctions.sql so the
-- notification list has realistic entries to click through locally:
--   * auction-created notifications for 5 open auctions (3000001-3000005)
--   * outbid notifications for auctions where user 1 was outbid
--     (3000004, 3000007, 3000010 - see 004's outbid comment)
--   * win notifications for 6 of user 1's recently ended auctions
--     (3000101-3000106)
-- Some rows are seeded as already read so the read/unread filter has
-- something to filter.

SET NAMES utf8mb4;
SET time_zone = '+09:00';
USE `dbidding`;
START TRANSACTION;

DELETE FROM `notification`
WHERE `user_id` = 1
  AND `auction_id` IN (
    3000001, 3000002, 3000003, 3000004, 3000005,
    3000007, 3000010,
    3000101, 3000102, 3000103, 3000104, 3000105, 3000106
  );

-- Auction-created notifications (for cards user 1 has wishlisted).
INSERT INTO `notification` (`user_id`, `auction_id`, `message`, `is_read`, `created_at`)
SELECT
  1,
  `auctions`.`id`,
  CONCAT(`card_metadata`.`name`, ' 카드의 경매가 등록되었습니다.'),
  `auctions`.`id` <= 3000003,
  TIMESTAMPADD(HOUR, -(`auctions`.`id` - 3000000), NOW(6))
FROM `auctions`
JOIN `card_metadata` ON `card_metadata`.`id` = `auctions`.`item_id`
WHERE `auctions`.`id` IN (3000001, 3000002, 3000003, 3000004, 3000005);

-- Outbid notifications (user 1 was the previous leading bidder).
INSERT INTO `notification` (`user_id`, `auction_id`, `message`, `is_read`, `created_at`)
SELECT
  1,
  `auctions`.`id`,
  CONCAT(`card_metadata`.`name`, ' 카드 경매에 상회 입찰이 발생했습니다.'),
  FALSE,
  TIMESTAMPADD(MINUTE, -(30 * (`auctions`.`id` - 3000000)), NOW(6))
FROM `auctions`
JOIN `card_metadata` ON `card_metadata`.`id` = `auctions`.`item_id`
WHERE `auctions`.`id` IN (3000004, 3000007, 3000010);

-- Win notifications (user 1 is the final bidder on these ended auctions).
INSERT INTO `notification` (`user_id`, `auction_id`, `message`, `is_read`, `created_at`)
SELECT
  1,
  `auctions`.`id`,
  CONCAT(`card_metadata`.`name`, ' 카드 경매에 낙찰되었습니다.'),
  `auctions`.`id` <= 3000103,
  `auctions`.`close_time`
FROM `auctions`
JOIN `card_metadata` ON `card_metadata`.`id` = `auctions`.`item_id`
WHERE `auctions`.`id` IN (3000101, 3000102, 3000103, 3000104, 3000105, 3000106);

COMMIT;
