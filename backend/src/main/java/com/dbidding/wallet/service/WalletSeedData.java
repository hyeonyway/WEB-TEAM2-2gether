package com.dbidding.wallet.service;

import com.dbidding.wallet.repository.WalletBootstrapRow;
import com.dbidding.wallet.repository.WalletHeldHoldRow;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 배치 콜드시드 조회 결과를 유저 1명 기준으로 묶은 것. */
public record WalletSeedData(WalletBootstrapRow wallet, List<WalletHeldHoldRow> holds) {

    /** userId 목록에 대한 지갑/hold 배치 조회를 한 번씩만 실행하고, 유저별로 묶어서 돌려준다. */
    static Map<Integer, WalletSeedData> resolveBatch(
            List<Integer> userIds, WalletHoldRepository walletHoldRepository, WalletRepository walletRepository
    ) {
        List<WalletHeldHoldRow> holds = walletHoldRepository.findHeldRowsForUsers(userIds);
        List<WalletBootstrapRow> wallets = walletRepository.findBootstrapRowsForUsers(userIds);
        Map<Integer, List<WalletHeldHoldRow>> holdsByUser = holds.stream()
                .collect(Collectors.groupingBy(WalletHeldHoldRow::getUserId));
        return wallets.stream().collect(Collectors.toMap(
                WalletBootstrapRow::getUserId,
                wallet -> new WalletSeedData(wallet, holdsByUser.getOrDefault(wallet.getUserId(), List.of())),
                (first, second) -> first
        ));
    }
}
