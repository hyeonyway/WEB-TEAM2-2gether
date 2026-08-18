package com.dbidding.wallet.service;

import com.dbidding.auction.stream.WalletStateChangedStreamEvent;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.domain.WalletHold;
import com.dbidding.wallet.domain.PointRecord;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.sse.WalletBalanceChangedEvent;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stream 재전달에도 더 낮은 walletVersion이 현재 projection을 덮어쓰지 않게 한다. */
@Service
public class WalletProjectionService {
    private final WalletRepository walletRepository;
    private final PointRecordRepository pointRecordRepository;
    private final WalletHoldRepository walletHoldRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public WalletProjectionService(
            WalletRepository walletRepository,
            PointRecordRepository pointRecordRepository,
            WalletHoldRepository walletHoldRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.walletRepository = walletRepository;
        this.pointRecordRepository = pointRecordRepository;
        this.walletHoldRepository = walletHoldRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 기존 projection 단위 테스트의 생성자 계약을 유지한다. */
    WalletProjectionService(
            WalletRepository walletRepository,
            PointRecordRepository pointRecordRepository,
            WalletHoldRepository walletHoldRepository
    ) {
        this(walletRepository, pointRecordRepository, walletHoldRepository, event -> { }, Clock.systemUTC());
    }

    @Transactional
    public void project(WalletStateChangedStreamEvent event) {
        Wallet wallet = walletRepository.findByUserId(event.userId())
                .orElseThrow(() -> new IllegalStateException("지갑 projection 대상이 없습니다: " + event.userId()));
        int updated = walletRepository.updateProjectionIfNewer(
                event.userId(), event.availableBalance() + event.frozenBalance(), event.walletVersion());
        // updateProjectionIfNewer가 버전 가드로 잔액을 안 바꿨다면(stale 이벤트), 거래내역/hold도
        // 같이 건너뛴다 - 잔액만 보호하고 나머지 부수효과는 그대로 반영하면 거래내역엔 기록이 남는데
        // 잔액은 안 바뀌는 유령 기록, hold 상태 오염으로 인한 frozenBalance 오계산이 생긴다.
        if (updated == 0) return;
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
        // Redis 승인 직후 이미 wallet SSE를 발행한다. projection은 MySQL 반영만 담당한다.
    }
}
