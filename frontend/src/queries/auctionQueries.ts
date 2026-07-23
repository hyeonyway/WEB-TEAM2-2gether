import {keepPreviousData,queryOptions} from '@tanstack/react-query';
import {fetchAuctions,fetchCards} from '../api/auctionApi';
import type {AuctionListRequestDto,CardListRequestDto} from '../dto/auctionDto';

export const auctionQueryKeys={
  all:['auctions'] as const,
  list:(query:AuctionListRequestDto)=>[...auctionQueryKeys.all,'list',query] as const,
};

export const cardQueryKeys={
  all:['cards'] as const,
  list:(query:CardListRequestDto)=>[...cardQueryKeys.all,'list',query] as const,
};

export const auctionQueries={
  list:(query:AuctionListRequestDto)=>queryOptions({
    queryKey:auctionQueryKeys.list(query),
    queryFn:()=>fetchAuctions(query),
    placeholderData:keepPreviousData,
    staleTime:30_000,
  }),
};

export const cardQueries={
  list:(query:CardListRequestDto)=>queryOptions({
    queryKey:cardQueryKeys.list(query),
    queryFn:()=>fetchCards(query),
    placeholderData:keepPreviousData,
    staleTime:60_000,
  }),
};
