package com.dbidding.wallet.service;

import com.dbidding.auction.stream.WalletStateChangedStreamEvent;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.domain.WalletHold;
import com.dbidding.wallet.domain.PointRecord;
import com.dbidding.wallet.domain.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stream 재전달에도 더 낮은 walletVersion이 현재 projection을 덮어쓰지 않게 한다. */
@Service
@RequiredArgsConstructor
public class WalletProjectionService {
    private final WalletRepository walletRepository;
    private final PointRecordRepository pointRecordRepository;
    private final WalletHoldRepository walletHoldRepository;

    @Transactional
    public void project(WalletStateChangedStreamEvent event) {
        Wallet wallet = walletRepository.findByUserId(event.userId())
                .orElseThrow(() -> new IllegalStateException("지갑 projection 대상이 없습니다: " + event.userId()));
        if (event.transactionType() != null && !pointRecordRepository.existsByEventId(event.eventId())) {
            pointRecordRepository.save(PointRecord.projected(
                    wallet.getId(), event.auctionId(), event.transactionAmount(),
                    event.availableBalance() + event.frozenBalance(), event.transactionType(),
                    event.idempotencyKey(), event.eventId()
            ));
        }
        if (event.holdStatus() != null && !walletHoldRepository.existsByEventId(event.eventId())) {
            walletHoldRepository.findTopByWalletIdAndAuctionIdOrderByIdDesc(wallet.getId(), event.auctionId())
                    .ifPresentOrElse(hold -> {
                        hold.applyProjection(event.holdAmount(), event.holdStatus(), event.walletVersion(), event.eventId());
                        walletHoldRepository.save(hold);
                    }, () -> walletHoldRepository.save(WalletHold.projected(
                            wallet.getId(), event.auctionId(), event.holdAmount(), event.holdStatus(), event.walletVersion(), event.eventId()
                    )));
        }
        walletRepository.updateProjectionIfNewer(event.userId(), event.availableBalance() + event.frozenBalance(), event.walletVersion());
    }
}
