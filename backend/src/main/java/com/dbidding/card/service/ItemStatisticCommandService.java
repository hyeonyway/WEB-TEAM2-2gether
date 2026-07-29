package com.dbidding.card.service;

import com.dbidding.card.repository.CardMetadataRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ItemStatisticCommandService {
    private final CardMetadataRepository cardRepository;

    public ItemStatisticCommandService(CardMetadataRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public void recordBid(Integer itemId, LocalDateTime date) {
        validateItem(itemId);
        // 입찰량은 종료된 날짜의 일간 배치에서 실제 bids 행을 집계한다.
    }

    public void recordAuctionOpened(Integer itemId, LocalDateTime date) {
        validateItem(itemId);
    }

    public void recordAuctionCompleted(Integer itemId, long winningPrice, LocalDateTime date) {
        validateItem(itemId);
        if (winningPrice <= 0) {
            throw new IllegalArgumentException("낙찰가는 0보다 커야 합니다.");
        }
    }

    public void recordAuctionClosedWithoutTrade(Integer itemId, LocalDateTime date) {
        validateItem(itemId);
    }

    private void validateItem(Integer itemId) {
        if (itemId == null || !cardRepository.existsById(itemId)) {
            throw new IllegalArgumentException("존재하지 않는 카드입니다.");
        }
    }
}
