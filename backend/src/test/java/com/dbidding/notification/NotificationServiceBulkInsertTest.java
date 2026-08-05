package com.dbidding.notification;

import static org.assertj.core.api.Assertions.assertThat;

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

        List<Notification> result = notificationService.saveAllIgnoringDuplicates(
                userIds, auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."
        );

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Notification::getUserId).containsExactlyInAnyOrderElementsOf(userIds);
        Notification firstUserResult = result.stream()
                .filter(notification -> notification.getUserId().equals(userIds.get(0)))
                .findFirst()
                .orElseThrow();
        assertThat(firstUserResult.getId()).isEqualTo(existing.getId());

        long totalRows = result.stream()
                .filter(notification -> notification.getAuctionId().equals(auctionId))
                .count();
        assertThat(totalRows).isEqualTo(3);
    }

    @Test
    void 대상_유저가_모두_새로우면_전부_저장한다() {
        List<Notification> result = notificationService.saveAllIgnoringDuplicates(
                userIds, auctionId, NotificationType.AUCTION_OPENED, "리자몽 EX 카드의 경매가 등록되었습니다."
        );

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Notification::getUserId).containsExactlyInAnyOrderElementsOf(userIds);
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
