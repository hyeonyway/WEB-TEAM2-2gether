package com.dbidding.card.service;

import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.card.domain.ItemStatistic;
import com.dbidding.card.repository.ItemStatisticRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ItemStatisticCommandService {
    private final CardMetadataRepository cardRepository;
    private final ItemStatisticRepository statisticRepository;

    public ItemStatisticCommandService(
            CardMetadataRepository cardRepository,
            ItemStatisticRepository statisticRepository
    ) {
        this.cardRepository = cardRepository;
        this.statisticRepository = statisticRepository;
    }

    public void recordBid(Integer itemId, LocalDateTime date) {
        validateItem(itemId);
        statisticRepository.incrementBidCount(itemId, date);
    }

    public void recordAuctionOpened(Integer itemId, LocalDateTime date) {
        validateItem(itemId);
        statisticRepository.refreshActiveAuctionCount(itemId, date);
    }

    public void recordAuctionCompleted(Integer itemId, long winningPrice, LocalDateTime date) {
        validateItem(itemId);
        if (winningPrice <= 0) {
            throw new IllegalArgumentException("낙찰가는 0보다 커야 합니다.");
        }
        statisticRepository.recordCompletedAuction(itemId, date, winningPrice);
        statisticRepository.refreshActiveAuctionCount(itemId, date);
        updateChangeRates(itemId, date);
    }

    public void recordAuctionClosedWithoutTrade(Integer itemId, LocalDateTime date) {
        validateItem(itemId);
        statisticRepository.refreshActiveAuctionCount(itemId, date);
    }

    private void updateChangeRates(Integer itemId, LocalDateTime date) {
        ItemStatistic current = statisticRepository.findByItemIdAndStatisticsDate(itemId, date)
                .orElseThrow(() -> new IllegalStateException("생성된 통계 행을 찾을 수 없습니다."));
        current.updateChangeRates(
                changeRate(current, previous(itemId, date.minusDays(1))),
                changeRate(current, previous(itemId, date.minusDays(7))),
                changeRate(current, previous(itemId, date.minusDays(30)))
        );
    }

    private ItemStatistic previous(Integer itemId, LocalDateTime date) {
        return statisticRepository
                .findFirstByItemIdAndStatisticsDateLessThanEqualOrderByStatisticsDateDesc(itemId, date)
                .orElse(null);
    }

    private BigDecimal changeRate(ItemStatistic current, ItemStatistic previous) {
        if (previous == null || price(previous) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(price(current) - price(previous))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(price(previous)), 2, RoundingMode.HALF_UP);
    }

    private long price(ItemStatistic statistic) {
        if (statistic.getLatestPrice() != null) {
            return statistic.getLatestPrice();
        }
        return statistic.getAvgPrice() == null ? 0 : statistic.getAvgPrice();
    }

    private void validateItem(Integer itemId) {
        if (itemId == null || !cardRepository.existsById(itemId)) {
            throw new IllegalArgumentException("존재하지 않는 카드입니다.");
        }
    }
}
