import {queryOptions} from '@tanstack/react-query';
import {fetchParticipatingAuctions,fetchRecentWins} from '../api/dashboardApi';
import type {ParticipatingAuctionSort,RecentWinSort} from '../api/dashboardApi';

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
