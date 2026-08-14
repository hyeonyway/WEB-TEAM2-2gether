package com.dbidding.auction.adapter;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.card.port.CardAuctionPort;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!redis")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CardAuctionAdapter implements CardAuctionPort {
    private final AuctionRepository auctionRepository;
    private final Clock clock;

    @Override
    public int countActiveAuctions(Integer cardId) {
        return Math.toIntExact(auctionRepository.countByItemIdAndStatusInAndCloseTimeAfter(
                cardId,
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                clock.instant()
        ));
    }
}
