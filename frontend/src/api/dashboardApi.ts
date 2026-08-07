import type {AuctionDto,CardTheme} from '../dto/auctionDto';
import type {DashboardAuctionResponseDto} from '../dto/dashboardDto';
import {authenticatedRequest} from './authenticatedRequest';
import {resolveImageUrl} from './auctionMapper';

const themes:CardTheme[]=['gold','water','dark','multi','sketch'];

const mapDashboardAuction=(dto:DashboardAuctionResponseDto):AuctionDto=>({
  id:dto.id,
  sellerId:dto.seller_id,
  card:{
    id:dto.card.id,
    name:dto.card.name,
    marketPrice:dto.current_price,
    lowPrice:dto.current_price,
    highPrice:dto.current_price,
    changeRate:0,
    theme:themes[dto.card.id%themes.length],
    bidCount:dto.bid_count,
    psaGrade:String(dto.card.psa_grade??'-'),
    language:dto.card.language==='EN'?'EN':dto.card.language==='KR'?'KR':'JP',
    imageUrl:resolveImageUrl(dto.card.thumbnail_url),
  },
  startPrice:dto.start_price,
  currentPrice:dto.current_price,
  bidIncrement:dto.bid_increment,
  bidCount:dto.bid_count,
  endsAt:dto.ends_at,
  status:dto.status,
  version:dto.version,
  myBidStatus:dto.my_bid_status,
  myBidAmount:dto.my_bid_amount,
});

export type ParticipatingAuctionSort='ENDING_SOON'|'PRICE_HIGH';
export type RecentWinSort='LATEST'|'OLDEST'|'PRICE_HIGH';

export async function fetchParticipatingAuctions(sort:ParticipatingAuctionSort){
  const search=new URLSearchParams({sort});
  const response=await authenticatedRequest<DashboardAuctionResponseDto[]>(`/api/dashboard/participating-auctions?${search}`);
  return response.map(mapDashboardAuction);
}

export async function fetchRecentWins(sort:RecentWinSort){
  const search=new URLSearchParams({sort});
  const response=await authenticatedRequest<DashboardAuctionResponseDto[]>(`/api/dashboard/recent-wins?${search}`);
  return response.map(mapDashboardAuction);
}
