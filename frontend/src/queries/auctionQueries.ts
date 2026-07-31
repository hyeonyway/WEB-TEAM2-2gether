import {infiniteQueryOptions,keepPreviousData,queryOptions} from '@tanstack/react-query';
import {fetchAuctionBidContext,fetchAuctionDetail,fetchAuctions,fetchCardDetail,fetchCardPage,fetchCards} from '../api/auctionApi';
import type {AuctionListRequestDto,CardListRequestDto} from '../dto/auctionDto';

export const auctionQueryKeys={
  all:['auctions'] as const,
  list:(query:AuctionListRequestDto)=>[...auctionQueryKeys.all,'list',query] as const,
  detail:(auctionId:number)=>[...auctionQueryKeys.all,'detail',auctionId] as const,
  bidContext:(auctionId:number)=>[...auctionQueryKeys.all,'bid-context',auctionId] as const,
};

export const cardQueryKeys={
  all:['cards'] as const,
  list:(query:CardListRequestDto)=>[...cardQueryKeys.all,'list',query] as const,
  infiniteList:(query:CardListRequestDto)=>[...cardQueryKeys.all,'infinite-list',query] as const,
  detail:(cardId:number)=>[...cardQueryKeys.all,'detail',cardId] as const,
};

export const auctionQueries={
  list:(query:AuctionListRequestDto)=>queryOptions({
    queryKey:auctionQueryKeys.list(query),
    queryFn:()=>fetchAuctions(query),
    placeholderData:keepPreviousData,
    staleTime:30_000,
  }),
  detail:(auctionId:number)=>queryOptions({
    queryKey:auctionQueryKeys.detail(auctionId),
    queryFn:()=>fetchAuctionDetail(auctionId),
    staleTime:15_000,
  }),
  bidContext:(auctionId:number)=>queryOptions({
    queryKey:auctionQueryKeys.bidContext(auctionId),
    queryFn:()=>fetchAuctionBidContext(auctionId),
    staleTime:5_000,
  }),
};

export const cardQueries={
  list:(query:CardListRequestDto)=>queryOptions({
    queryKey:cardQueryKeys.list(query),
    queryFn:()=>fetchCards(query),
    placeholderData:keepPreviousData,
    staleTime:60_000,
  }),
  detail:(cardId:number)=>queryOptions({
    queryKey:cardQueryKeys.detail(cardId),
    queryFn:()=>fetchCardDetail(cardId),
    staleTime:60_000,
  }),
  infiniteList:(query:CardListRequestDto)=>infiniteQueryOptions({
    queryKey:cardQueryKeys.infiniteList(query),
    queryFn:({pageParam})=>fetchCardPage(query,pageParam),
    initialPageParam:0,
    getNextPageParam:lastPage=>lastPage.has_next?lastPage.page+1:undefined,
    staleTime:60_000,
  }),
};
