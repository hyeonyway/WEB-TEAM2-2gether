package com.dbidding.wallet.service;

import com.dbidding.auction.stream.WalletStateChangedStreamEvent;
import com.dbidding.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Stream 재전달에도 더 낮은 walletVersion이 현재 projection을 덮어쓰지 않게 한다. */
@Service
@RequiredArgsConstructor
public class WalletProjectionService {
    private final WalletRepository walletRepository;
    public void project(WalletStateChangedStreamEvent event) {
        walletRepository.updateProjectionIfNewer(event.userId(), event.availableBalance() + event.frozenBalance(), event.walletVersion());
    }
}
