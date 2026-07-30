package com.dbidding.auction.adapter;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.card.port.CardAuctionPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CardAuctionAdapter implements CardAuctionPort {
    private final AuctionRepository auctionRepository;

    @Override
    public int countActiveAuctions(Integer cardId) {
        return Math.toIntExact(auctionRepository.countByItemIdAndStatusIn(
                cardId, List.of(AuctionStatus.OPEN, AuctionStatus.ENDING)));
    }
}
