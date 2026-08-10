import {queryOptions} from '@tanstack/react-query';
import {fetchParticipatingAuctions,fetchRecentWins} from '../api/dashboardApi';
import type {ParticipatingAuctionSort,RecentWinSort} from '../api/dashboardApi';
import type {AuctionDto} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';
import {applyAuctionEvent} from './auctionStreamCache';

export const dashboardQueryKey=['dashboard'] as const;

export const dashboardQueries={
  participating:(sort:ParticipatingAuctionSort)=>queryOptions({
    queryKey:[...dashboardQueryKey,'participating-auctions',sort],
    queryFn:()=>fetchParticipatingAuctions(sort),
    // 탭/정렬을 다시 선택하면 캐시 대신 현재 참여 상태를 조회한다.
    staleTime:0,
  }),
  recentWins:(sort:RecentWinSort)=>queryOptions({
    queryKey:[...dashboardQueryKey,'recent-wins',sort],
    queryFn:()=>fetchRecentWins(sort),
    // 낙찰 목록도 탭 전환 시점의 최신 상태를 보여 준다.
    staleTime:0,
  }),
};

export function applyDashboardAuctionEvent(
  auctions:AuctionDto[]|undefined,
  event:AuctionStreamPayload,
  sort:ParticipatingAuctionSort,
){
  const next=applyAuctionEvent(auctions,event);
  return [...next].sort((left,right)=>
    sort==='ENDING_SOON'
      ? Date.parse(left.endsAt)-Date.parse(right.endsAt)
      : right.currentPrice-left.currentPrice,
  );
}
