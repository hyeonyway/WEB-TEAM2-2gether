package com.dbidding.dashboard.service;

import com.dbidding.dashboard.domain.ParticipatingAuctionSort;
import com.dbidding.dashboard.domain.RecentWinSort;
import com.dbidding.dashboard.dto.DashboardResponse;
import java.util.List;

/** 대시보드의 조회 원본(DB projection 또는 Redis 승인 상태)을 분리한다. */
public interface DashboardQueryService {
    List<DashboardResponse.AuctionSnapshot> getParticipatingAuctions(Integer userId, ParticipatingAuctionSort sort);

    List<DashboardResponse.AuctionSnapshot> getRecentWins(Integer userId, RecentWinSort sort);
}
