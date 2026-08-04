import {infiniteQueryOptions,keepPreviousData,queryOptions} from '@tanstack/react-query';
import {fetchAuctionBidContext,fetchAuctionBids,fetchAuctionDetail,fetchAuctions,fetchCardDetail,fetchCardPage,fetchCards} from '../api/auctionApi';
import type {InfiniteData} from '@tanstack/react-query';
import type {AuctionDto,AuctionListRequestDto,BidContextResponseDto,CardListRequestDto,CursorPageResponseDto} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';
import {applyAuctionEvent,eventToAuction,myBidStatusAfterEvent,sortAuctions} from './auctionStreamCache';

export type AuctionViewerScope='public'|'self';

export const auctionQueryKeys={
  all:['auctions'] as const,
  lists:()=>[...auctionQueryKeys.all,'list'] as const,
  list:(query:AuctionListRequestDto,viewerScope:AuctionViewerScope)=>[...auctionQueryKeys.lists(),viewerScope,query] as const,
  detail:(auctionId:number,viewerScope:AuctionViewerScope)=>[...auctionQueryKeys.all,'detail',auctionId,viewerScope] as const,
  bidContext:(auctionId:number)=>[...auctionQueryKeys.all,'bid-context',auctionId] as const,
  bids:(auctionId:number)=>[...auctionQueryKeys.all,'bids',auctionId] as const,
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
    staleTime:30_000,
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

const matchesAuctionQuery=(auction:AuctionDto,query:AuctionListRequestDto)=>
  auction.card.name.toLowerCase().includes(query.keyword.trim().toLowerCase())
  &&(query.psaGrade===null||auction.card.psaGrade===query.psaGrade);

export function applyAuctionListEvent(
  data:InfiniteData<CursorPageResponseDto<AuctionDto>,string|undefined>|undefined,
  event:AuctionStreamPayload,
  query:AuctionListRequestDto,
):InfiniteData<CursorPageResponseDto<AuctionDto>,string|undefined>|undefined{
  if(!data)return data;
  if(event.type==='BID_PLACED'){
    return {
      ...data,
      pages:data.pages.map(page=>({
        ...page,
        content:sortAuctions(applyAuctionEvent(page.content,event),query.sort),
      })),
    };
  }
  if(event.type==='AUCTION_CREATED'){
    const created=eventToAuction(event);
    if(!matchesAuctionQuery(created,query))return data;
    return {
      ...data,
      pages:data.pages.map((page,index)=>index===0?{
        ...page,
        content:sortAuctions(
          [created,...page.content.filter(auction=>auction.id!==created.id)],
          query.sort,
        ).slice(0,query.size),
      }:page),
    };
  }
  const closedCard={
    ...eventToAuction({...event,type:'AUCTION_CREATED'}),
    status:event.status,
  };
  if(!matchesAuctionQuery(closedCard,query))return data;
  return {
    ...data,
    pages:data.pages.map(page=>({
      ...page,
      content:page.content.filter(auction=>auction.id!==event.auction_id),
    })),
  };
}

export function applyBidContextEvent(
  context:BidContextResponseDto|undefined,
  event:AuctionStreamPayload,
):BidContextResponseDto|undefined{
  if(!context||context.auction_id!==event.auction_id||context.version>=event.auction_version)return context;
  const currentPrice=event.current_price??event.final_price??context.current_price;
  const recentBids=event.type==='BID_PLACED'
    ?[{
      id:-event.auction_version,
      amount:currentPrice,
      bidder_alias:`user-${String(event.bidder_id).slice(0,2)}***`,
      is_highest:true,
      created_at:event.occurred_at,
    },...context.recent_bids.map(bid=>({...bid,is_highest:false}))].slice(0,5)
    :context.recent_bids;
  return {
    ...context,
    status:event.status,
    version:event.auction_version,
    current_price:currentPrice,
    minimum_bid:currentPrice+event.bid_increment,
    bid_increment:event.bid_increment,
    my_bid_status:myBidStatusAfterEvent(context.my_bid_status,event),
    recent_bids:recentBids,
  };
}
