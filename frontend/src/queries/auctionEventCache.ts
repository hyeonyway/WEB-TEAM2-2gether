import type {QueryClient,QueryKey} from '@tanstack/react-query';
import type {ParticipatingAuctionSort,RecentWinSort} from '../api/dashboardApi';
import {resolveImageUrl} from '../api/auctionMapper';
import type {AuctionDto,AuctionListRequestDto,CardTheme} from '../dto/auctionDto';
import type {AuctionUpdatedEventDto} from '../dto/auctionEventDto';

const themes:CardTheme[]=['gold','water','dark','multi','sketch'];

const eventPrice=(event:AuctionUpdatedEventDto)=>
  event.type==='AUCTION_CLOSED'?event.final_price:event.current_price;

export function auctionFromEvent(
  event:AuctionUpdatedEventDto,
  myBidStatus:AuctionDto['myBidStatus']='NONE',
  myBidAmount:number|null=null,
):AuctionDto{
  const price=eventPrice(event);
  return {
    id:event.auction_id,
    card:{
      id:event.card_id,
      name:event.card_name,
      marketPrice:price,
      lowPrice:price,
      highPrice:price,
      changeRate:0,
      theme:themes[event.card_id%themes.length],
      bidCount:event.bid_count,
      psaGrade:event.card_psa_grade??'-',
      language:event.card_language==='EN'?'EN':event.card_language==='KR'?'KR':'JP',
      imageUrl:resolveImageUrl(event.card_thumbnail_url),
    },
    startPrice:event.start_price,
    currentPrice:price,
    bidIncrement:event.bid_increment,
    bidCount:event.bid_count,
    endsAt:event.type==='AUCTION_CLOSED'?event.closed_at:event.ends_at,
    status:event.status,
    myBidStatus,
    myBidAmount,
    version:event.auction_version,
  };
}

function mergeAuction(current:AuctionDto,event:AuctionUpdatedEventDto){
  if(current.version>=event.auction_version)return current;
  const snapshot=auctionFromEvent(event,current.myBidStatus,current.myBidAmount);
  return {...snapshot,card:{...snapshot.card,theme:current.card.theme}};
}

function matches(auction:AuctionDto,query:AuctionListRequestDto){
  const keyword=query.keyword.trim().toLowerCase();
  return (!keyword||auction.card.name.toLowerCase().includes(keyword))
    &&(query.psaGrade===null||auction.card.psaGrade===String(query.psaGrade));
}

function sortAuctions(auctions:AuctionDto[],sort:AuctionListRequestDto['sort']){
  const copy=[...auctions];
  copy.sort((a,b)=>{
    const difference=switchSort(a,b,sort);
    return difference||a.id-b.id;
  });
  return copy;
}

function switchSort(a:AuctionDto,b:AuctionDto,sort:AuctionListRequestDto['sort']){
  switch(sort){
    case 'BID_COUNT':return b.bidCount-a.bidCount;
    case 'PRICE_HIGH':return b.currentPrice-a.currentPrice;
    case 'PRICE_LOW':return a.currentPrice-b.currentPrice;
    case 'CHANGE_HIGH':{
      const aRate=a.startPrice>0?(a.currentPrice-a.startPrice)/a.startPrice:0;
      const bRate=b.startPrice>0?(b.currentPrice-b.startPrice)/b.startPrice:0;
      return bRate-aRate;
    }
  }
}

export function updateAuctionList(
  current:AuctionDto[]|undefined,
  query:AuctionListRequestDto,
  event:AuctionUpdatedEventDto,
){
  if(!current)return current;
  if(event.type==='AUCTION_CLOSED'){
    const existing=current.find(auction=>auction.id===event.auction_id);
    if(existing&&existing.version>=event.auction_version)return current;
    return current.filter(auction=>auction.id!==event.auction_id);
  }
  const index=current.findIndex(auction=>auction.id===event.auction_id);
  const snapshot=index<0?auctionFromEvent(event):mergeAuction(current[index],event);
  const without=current.filter(auction=>auction.id!==event.auction_id);
  return matches(snapshot,query)?sortAuctions([...without,snapshot],query.sort):without;
}

function participatingSort(auctions:AuctionDto[],sort:ParticipatingAuctionSort){
  return [...auctions].sort((a,b)=>{
    const difference=sort==='PRICE_HIGH'
      ?b.currentPrice-a.currentPrice
      :new Date(a.endsAt).getTime()-new Date(b.endsAt).getTime();
    return difference||a.id-b.id;
  });
}

function recentWinSort(auctions:AuctionDto[],sort:RecentWinSort){
  return [...auctions].sort((a,b)=>{
    const difference=sort==='PRICE_HIGH'
      ?b.currentPrice-a.currentPrice
      :sort==='OLDEST'
        ?new Date(a.endsAt).getTime()-new Date(b.endsAt).getTime()
        :new Date(b.endsAt).getTime()-new Date(a.endsAt).getTime();
    return difference||a.id-b.id;
  });
}

export function updateParticipatingAuctions(
  current:AuctionDto[]|undefined,
  sort:ParticipatingAuctionSort,
  event:AuctionUpdatedEventDto,
  currentUserId:number|null,
){
  if(!current)return current;
  if(event.type==='AUCTION_CREATED')return current;
  if(event.type==='AUCTION_CLOSED'){
    const existing=current.find(auction=>auction.id===event.auction_id);
    if(existing&&existing.version>=event.auction_version)return current;
    return current.filter(auction=>auction.id!==event.auction_id);
  }
  const existing=current.find(auction=>auction.id===event.auction_id);
  const isBidder=event.bidder_id===currentUserId;
  const wasPrevious=event.previous_bidder_id===currentUserId;
  if(!existing&&!isBidder&&!wasPrevious)return current;
  if(existing&&existing.version>=event.auction_version)return current;
  const status=isBidder?'LEADING':wasPrevious?'OUTBID':existing?.myBidStatus??'NONE';
  const amount=isBidder?event.bid_price:existing?.myBidAmount??null;
  const snapshot=auctionFromEvent(event,status,amount);
  return participatingSort([
    ...current.filter(auction=>auction.id!==event.auction_id),
    snapshot,
  ],sort);
}

export function updateRecentWins(
  current:AuctionDto[]|undefined,
  sort:RecentWinSort,
  event:AuctionUpdatedEventDto,
  currentUserId:number|null,
){
  if(!current||event.type!=='AUCTION_CLOSED')return current;
  const without=current.filter(auction=>auction.id!==event.auction_id);
  if(event.winner_id!==currentUserId)return without;
  const existing=current.find(auction=>auction.id===event.auction_id);
  if(existing&&existing.version>=event.auction_version)return current;
  return recentWinSort([
    ...without,
    auctionFromEvent(event,'LEADING',event.final_price),
  ],sort);
}

function queryPart<T>(key:QueryKey,index:number):T|null{
  return (key.length>index?key[index]:null) as T|null;
}

export function applyAuctionEvents(queryClient:QueryClient,events:AuctionUpdatedEventDto[]){
  for(const event of events){
    queryClient.getQueryCache().findAll({queryKey:['auctions','list']}).forEach(query=>{
      const request=queryPart<AuctionListRequestDto>(query.queryKey,2);
      if(request){
        queryClient.setQueryData<AuctionDto[]>(
          query.queryKey,
          current=>updateAuctionList(current,request,event),
        );
      }
    });
  }
}

export function applyDashboardEvents(
  queryClient:QueryClient,
  events:AuctionUpdatedEventDto[],
  currentUserId:number|null,
){
  for(const event of events){
    queryClient.getQueryCache().findAll({
      queryKey:['dashboard','participating-auctions'],
    }).forEach(query=>{
      const sort=queryPart<ParticipatingAuctionSort>(query.queryKey,2);
      if(sort){
        queryClient.setQueryData<AuctionDto[]>(
          query.queryKey,
          current=>updateParticipatingAuctions(current,sort,event,currentUserId),
        );
      }
    });
    queryClient.getQueryCache().findAll({
      queryKey:['dashboard','recent-wins'],
    }).forEach(query=>{
      const sort=queryPart<RecentWinSort>(query.queryKey,2);
      if(sort){
        queryClient.setQueryData<AuctionDto[]>(
          query.queryKey,
          current=>updateRecentWins(current,sort,event,currentUserId),
        );
      }
    });
  }
}
