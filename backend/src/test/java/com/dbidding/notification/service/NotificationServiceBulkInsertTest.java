package com.dbidding.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.notification.domain.Notification;
import com.dbidding.notification.domain.NotificationType;
import com.dbidding.notification.dto.NotificationInsertRow;
import com.dbidding.notification.repository.NotificationRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationService.class)
class NotificationServiceBulkInsertTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Integer auctionId;
    private List<Integer> userIds;

    @BeforeEach
    void setUp() {
        auctionId = 90_000 + (int) (Math.random() * 10_000);
        userIds = List.of(insertUser("bulk-1"), insertUser("bulk-2"), insertUser("bulk-3"));
    }

    @Test
    void 이미_저장된_유저는_건너뛰고_나머지만_새로_저장한다() {
        Notification existing = notificationService.save(
                userIds.get(0), auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."
        );

        notificationService.saveAllIgnoringDuplicates(
                userIds, auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."
        );

        List<Notification> saved = notificationRepository.findByBidIdAndAuctionIdInAndUserIdIn(
                Notification.NO_BID, List.of(auctionId), userIds
        );
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(Notification::getUserId).containsExactlyInAnyOrderElementsOf(userIds);
        Notification firstUserResult = saved.stream()
                .filter(notification -> notification.getUserId().equals(userIds.get(0)))
                .findFirst()
                .orElseThrow();
        assertThat(firstUserResult.getId()).isEqualTo(existing.getId());
    }

    @Test
    void 대상_유저가_모두_새로우면_전부_저장한다() {
        notificationService.saveAllIgnoringDuplicates(
                userIds, auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."
        );

        List<Notification> saved = notificationRepository.findByBidIdAndAuctionIdInAndUserIdIn(
                Notification.NO_BID, List.of(auctionId), userIds
        );
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(Notification::getUserId).containsExactlyInAnyOrderElementsOf(userIds);
    }

    @Test
    void 유저_수가_청크_크기와_정확히_같으면_한_청크로_전부_저장한다() {
        List<Integer> chunkSizedUserIds = insertUsersInBulk("chunk-exact", 10_000);

        notificationService.saveAllIgnoringDuplicates(
                chunkSizedUserIds, auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."
        );

        List<Notification> saved = notificationRepository.findByBidIdAndAuctionIdInAndUserIdIn(
                Notification.NO_BID, List.of(auctionId), chunkSizedUserIds
        );
        assertThat(saved).hasSize(10_000);
        assertThat(saved).extracting(Notification::getUserId).containsExactlyInAnyOrderElementsOf(chunkSizedUserIds);
    }

    @Test
    void 유저_수가_청크_크기를_넘으면_여러_청크로_나눠도_전부_저장한다() {
        List<Integer> overChunkUserIds = insertUsersInBulk("chunk-over", 10_001);

        notificationService.saveAllIgnoringDuplicates(
                overChunkUserIds, auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."
        );

        List<Notification> saved = notificationRepository.findByBidIdAndAuctionIdInAndUserIdIn(
                Notification.NO_BID, List.of(auctionId), overChunkUserIds
        );
        assertThat(saved).hasSize(10_001);
        assertThat(saved).extracting(Notification::getUserId).containsExactlyInAnyOrderElementsOf(overChunkUserIds);
    }

    @Test
    void 같은_경매_같은_타입이지만_수신자별로_메시지가_다른_행도_한번에_저장한다() {
        // #506: handleAuctionClosed/handleOrderCompleted/handleOrderCancelled가 낙찰자+판매자,
        // 구매자+판매자처럼 auctionId/type은 같고 메시지만 다른 두 행을 이 메서드 하나로 합쳐 INSERT한다.
        List<NotificationInsertRow> rows = List.of(
                NotificationInsertRow.of(userIds.get(0), auctionId, NotificationType.AUCTION_WON, "카드 경매에 낙찰되었습니다."),
                NotificationInsertRow.of(userIds.get(1), auctionId, NotificationType.AUCTION_WON, "카드 경매가 낙찰되었습니다.")
        );

        notificationService.insertAllIgnoringDuplicates(rows);

        List<Notification> saved = notificationRepository.findByBidIdAndAuctionIdInAndUserIdIn(
                Notification.NO_BID, List.of(auctionId), userIds
        );
        assertThat(saved).hasSize(2);
        assertThat(saved)
                .filteredOn(notification -> notification.getUserId().equals(userIds.get(0)))
                .extracting(Notification::getMessage)
                .containsExactly("카드 경매에 낙찰되었습니다.");
        assertThat(saved)
                .filteredOn(notification -> notification.getUserId().equals(userIds.get(1)))
                .extracting(Notification::getMessage)
                .containsExactly("카드 경매가 낙찰되었습니다.");
    }

    @Test
    void 여러_경매의_행을_insertAllIgnoringDuplicates로_한번에_저장한다() {
        Integer otherAuctionId = auctionId + 1;
        List<NotificationInsertRow> rows = List.of(
                NotificationInsertRow.of(userIds.get(0), auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."),
                NotificationInsertRow.of(userIds.get(1), auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."),
                NotificationInsertRow.of(userIds.get(2), otherAuctionId, NotificationType.AUCTION_OPENED, "피카츄 카드의 경매가 등록되었습니다.")
        );

        notificationService.insertAllIgnoringDuplicates(rows);

        List<Notification> saved = notificationRepository.findByBidIdAndAuctionIdInAndUserIdIn(
                Notification.NO_BID, List.of(auctionId, otherAuctionId), userIds
        );
        assertThat(saved).hasSize(3);
        assertThat(saved)
                .filteredOn(notification -> notification.getAuctionId().equals(otherAuctionId))
                .extracting(Notification::getMessage)
                .containsExactly("피카츄 카드의 경매가 등록되었습니다.");
    }

    @Test
    void insertAllIgnoringDuplicates는_이미_있는_행을_건너뛰고_나머지만_저장한다() {
        notificationService.save(
                userIds.get(0), auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."
        );
        List<NotificationInsertRow> rows = List.of(
                NotificationInsertRow.of(userIds.get(0), auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."),
                NotificationInsertRow.of(userIds.get(1), auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다.")
        );

        notificationService.insertAllIgnoringDuplicates(rows);

        List<Notification> saved = notificationRepository.findByBidIdAndAuctionIdInAndUserIdIn(
                Notification.NO_BID, List.of(auctionId), userIds
        );
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(userIds.get(0), userIds.get(1));
    }

    @Test
    void insertAllIgnoringDuplicates는_행이_청크_크기를_넘으면_여러_청크로_나눠도_전부_저장한다() {
        List<Integer> overChunkUserIds = insertUsersInBulk("insert-all-chunk-over", 10_001);
        List<NotificationInsertRow> rows = overChunkUserIds.stream()
                .map(userId -> NotificationInsertRow.of(userId, auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."))
                .toList();

        notificationService.insertAllIgnoringDuplicates(rows);

        List<Notification> saved = notificationRepository.findByBidIdAndAuctionIdInAndUserIdIn(
                Notification.NO_BID, List.of(auctionId), overChunkUserIds
        );
        assertThat(saved).hasSize(10_001);
        assertThat(saved).extracting(Notification::getUserId).containsExactlyInAnyOrderElementsOf(overChunkUserIds);
    }

    private List<Integer> insertUsersInBulk(String suffix, int count) {
        int startId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM users", Integer.class) + 1;
        List<Integer> userIds = new ArrayList<>(count);
        List<Object[]> batchArgs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = startId + i;
            userIds.add(id);
            batchArgs.add(new Object[] {
                    id, suffix + "-" + id + "@example.com", suffix + "-" + id, "USER", "ACTIVE", "a".repeat(64), "b".repeat(32)
            });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO users (id, email, nickname, role, status, encrypted_password, salt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                batchArgs
        );
        return userIds;
    }

    private Integer insertUser(String suffix) {
        Number generatedId = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("users")
                .usingColumns("email", "nickname", "role", "status", "encrypted_password", "salt")
                .usingGeneratedKeyColumns("id")
                .executeAndReturnKey(new MapSqlParameterSource()
                        .addValue("email", "notification-" + suffix + "@example.com")
                        .addValue("nickname", "notification-" + suffix)
                        .addValue("role", "USER")
                        .addValue("status", "ACTIVE")
                        .addValue("encrypted_password", "a".repeat(64))
                        .addValue("salt", "b".repeat(32)));
        return generatedId.intValue();
    }
}
