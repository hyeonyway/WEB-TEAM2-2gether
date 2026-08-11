import {infiniteQueryOptions,keepPreviousData,queryOptions} from '@tanstack/react-query';
import {fetchAuctionBidContext,fetchAuctionBids,fetchAuctionDetail,fetchAuctions,fetchCardDetail,fetchCardPage,fetchCards,fetchFailedAuctions} from '../api/auctionApi';
import type {AuctionListRequestDto,BidContextResponseDto,CardListRequestDto} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';
import {myBidStatusAfterEvent} from './auctionStreamCache';

export type AuctionViewerScope='public'|'self';

export const auctionQueryKeys={
  all:['auctions'] as const,
  lists:()=>[...auctionQueryKeys.all,'list'] as const,
  list:(query:AuctionListRequestDto,viewerScope:AuctionViewerScope)=>[...auctionQueryKeys.lists(),viewerScope,query] as const,
  detail:(auctionId:number,viewerScope:AuctionViewerScope)=>[...auctionQueryKeys.all,'detail',auctionId,viewerScope] as const,
  bidContext:(auctionId:number)=>[...auctionQueryKeys.all,'bid-context',auctionId] as const,
  bids:(auctionId:number)=>[...auctionQueryKeys.all,'bids',auctionId] as const,
  failedForSeller:()=>[...auctionQueryKeys.all,'failed-for-seller'] as const,
};

export const cardQueryKeys={
  all:['cards'] as const,
  list:(query:CardListRequestDto)=>[...cardQueryKeys.all,'list',query] as const,
  infiniteList:(query:CardListRequestDto)=>[...cardQueryKeys.all,'infinite-list',query] as const,
  detail:(cardId:number)=>[...cardQueryKeys.all,'detail',cardId] as const,
};

export const auctionQueries={
  list:(query:AuctionListRequestDto,viewerScope:AuctionViewerScope)=>infiniteQueryOptions({
    queryKey:auctionQueryKeys.list(query,viewerScope),
    queryFn:({pageParam})=>fetchAuctions(query,pageParam),
    initialPageParam:undefined as string|undefined,
    getNextPageParam:lastPage=>lastPage.has_next?lastPage.next_cursor??undefined:undefined,
    // 정렬을 전환할 때 이전 정렬의 캐시가 아닌 서버 정렬 결과를 사용한다.
    staleTime:0,
  }),
  detail:(auctionId:number,viewerScope:AuctionViewerScope)=>queryOptions({
    queryKey:auctionQueryKeys.detail(auctionId,viewerScope),
    queryFn:()=>fetchAuctionDetail(auctionId),
    staleTime:15_000,
  }),
  bids:(auctionId:number)=>queryOptions({
    queryKey:auctionQueryKeys.bids(auctionId),
    queryFn:()=>fetchAuctionBids(auctionId),
    staleTime:5_000,
  }),
  bidContext:(auctionId:number)=>queryOptions({
    queryKey:auctionQueryKeys.bidContext(auctionId),
    queryFn:()=>fetchAuctionBidContext(auctionId),
    staleTime:5_000,
  }),
  failedForSeller:()=>queryOptions({
    queryKey:auctionQueryKeys.failedForSeller(),
    queryFn:fetchFailedAuctions,
    staleTime:10_000,
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

export function applyBidContextEvent(
  context:BidContextResponseDto|undefined,
  event:AuctionStreamPayload,
):BidContextResponseDto|undefined{
  if(!context||context.auction_id!==event.auction_id||(context.eventId??0)>=event.event_id)return context;
  const currentPrice=event.current_price??event.final_price??context.current_price;
  const recentBids=event.type==='BID_PLACED'
    ?[{
      id:-event.event_id,
      amount:currentPrice,
      bidder_alias:`user-${String(event.bidder_id).slice(0,2)}***`,
      is_highest:true,
      created_at:event.occurred_at,
    },...context.recent_bids.map(bid=>({...bid,is_highest:false}))].slice(0,5)
    :context.recent_bids;
  return {
    ...context,
    status:event.status,
    eventId:event.event_id,
    current_price:currentPrice,
    minimum_bid:currentPrice+event.bid_increment,
    bid_increment:event.bid_increment,
    my_bid_status:myBidStatusAfterEvent(context.my_bid_status,event),
    recent_bids:recentBids,
  };
}
