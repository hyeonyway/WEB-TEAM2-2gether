package com.dbidding.wallet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dbidding.wallet.domain.PointRecord;

public interface PointRecordRepository extends JpaRepository<PointRecord, Long> {

	Optional<PointRecord> findByWalletIdAndIdempotencyKey(Integer walletId, String idempotencyKey);
}
