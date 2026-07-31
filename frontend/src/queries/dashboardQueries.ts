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
    staleTime:10_000,
  }),
  recentWins:(sort:RecentWinSort)=>queryOptions({
    queryKey:[...dashboardQueryKey,'recent-wins',sort],
    queryFn:()=>fetchRecentWins(sort),
    staleTime:10_000,
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
