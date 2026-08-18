package com.dbidding.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.domain.AuctionTimelineEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuctionTimelineEventRepositoryTest {

    @Autowired
    private AuctionTimelineEventRepository repository;

    @Test
    void ERROR_난_경매의_PENDING은_제외하고_다른_경매의_가장_오래된_PENDING을_고른다() {
        pending("stuck-1", 1, 100L);
        error("stuck-2", 1, 101L);
        AuctionTimelineEvent otherAuctionPending = pending("other-1", 2, 200L);

        List<Integer> blocked = repository.findAuctionIdsWithError();
        assertThat(blocked).containsExactly(1);

        List<AuctionTimelineEvent> eligible = repository.findEligiblePending(blocked, PageRequest.of(0, 1));

        assertThat(eligible).hasSize(1);
        assertThat(eligible.getFirst().getStreamId()).isEqualTo(otherAuctionPending.getStreamId());
    }

    @Test
    void ERROR가_없으면_블록된_경매_목록이_비어있다() {
        pending("ok-1", 1, 100L);
        processed("ok-2", 1, 101L);

        assertThat(repository.findAuctionIdsWithError()).isEmpty();
    }

    @Test
    void 경매의_마지막_이벤트_상태와_PENDING_ERROR_존재_여부를_경매_단위로_조회한다() {
        processed("a-1", 10, 1L);
        pending("a-2", 10, 2L);
        processed("b-1", 20, 1L);

        assertThat(repository.findFirstByAuctionIdOrderByIdDesc(10)).isPresent();
        assertThat(repository.findFirstByAuctionIdOrderByIdDesc(10).get().getStreamId()).isEqualTo("a-2");
        assertThat(repository.existsByAuctionIdAndProjectionStatus(10, AuctionBidEventProjectionStatus.PENDING)).isTrue();
        assertThat(repository.existsByAuctionIdAndProjectionStatus(20, AuctionBidEventProjectionStatus.PENDING)).isFalse();
    }

    private AuctionTimelineEvent pending(String streamId, Integer auctionId, Long auctionVersion) {
        return save(streamId, auctionId, auctionVersion, AuctionBidEventProjectionStatus.PENDING);
    }

    private AuctionTimelineEvent processed(String streamId, Integer auctionId, Long auctionVersion) {
        return save(streamId, auctionId, auctionVersion, AuctionBidEventProjectionStatus.PROCESSED);
    }

    private AuctionTimelineEvent error(String streamId, Integer auctionId, Long auctionVersion) {
        return save(streamId, auctionId, auctionVersion, AuctionBidEventProjectionStatus.ERROR);
    }

    private AuctionTimelineEvent save(String streamId, Integer auctionId, Long auctionVersion, AuctionBidEventProjectionStatus status) {
        AuctionTimelineEvent event = new AuctionTimelineEvent(
                streamId, auctionId, auctionVersion, "bid.accepted.v1", 1, "{}", Instant.now(), Instant.now()
        );
        if (status == AuctionBidEventProjectionStatus.PROCESSED) event.markProcessed(Instant.now());
        if (status == AuctionBidEventProjectionStatus.ERROR) event.markError("test");
        return repository.save(event);
    }
}
