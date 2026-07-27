import type {AuctionDto,AuctionResponseDto,CardDto,CardResponseDto,CardTheme} from '../dto/auctionDto';

const themes:CardTheme[]=['gold','water','dark','multi','sketch'];

export const resolveImageUrl=(path?:string|null):string|null=>{
  if(!path)return null;
  if(/^https?:\/\//i.test(path))return path;
  const base=(import.meta.env.VITE_IMAGE_BASE_URL??'https://dbidding.shop/upload').replace(/\/+$/,'');
  const normalizedPath=path.replace(/^\/+/,'').replace(/^upload\/+/,'');
  return `${base}/${normalizedPath}`;
};

export const mapCard=(dto:CardResponseDto):CardDto=>({
  id:dto.id,
  name:dto.name,
  marketPrice:dto.market_price,
  changeRate:dto.change_rate,
  theme:themes.includes(dto.theme as CardTheme)?dto.theme as CardTheme:'gold',
  bidCount:dto.bid_count,
  psaGrade:dto.psa_grade,
  language:dto.language==='EN'?'EN':dto.language==='KR'?'KR':'JP',
  imageUrl:resolveImageUrl(dto.thumbnail_url),
});

export const mapAuction=(dto:AuctionResponseDto):AuctionDto=>({
  id:dto.id,
  card:mapCard(dto.card),
  startPrice:dto.start_price??dto.current_price,
  currentPrice:dto.current_price,
  bidIncrement:dto.bid_increment??1000,
  bidCount:dto.bid_count,
  endsAt:dto.ends_at,
  status:dto.status,
  myBidStatus:dto.my_bid_status??'NONE',
  myBidAmount:dto.my_bid_amount??null,
});
