import type {AuctionCardResponseDto,AuctionDto,AuctionResponseDto,CardDto,CardResponseDto,CardTheme} from '../dto/auctionDto';

const themes:CardTheme[]=['gold','water','dark','multi','sketch'];

export const mapCardLanguage=(language:string|null|undefined):CardDto['language']=>{
  if(language==='EN'||language==='English')return 'EN';
  if(language==='KR'||language==='Korean')return 'KR';
  return 'JP';
};

export const normalizePsaGrade=(grade:string|null|undefined):string=>
  grade?.replace(/^PSA\s+/i,'').trim()||'-';

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
  lowPrice:dto.low_price??dto.market_price,
  highPrice:dto.high_price??dto.market_price,
  changeRate:dto.change_rate,
  theme:themes.includes(dto.theme as CardTheme)?dto.theme as CardTheme:'gold',
  bidCount:dto.bid_count,
  psaGrade:normalizePsaGrade(dto.psa_grade),
  language:mapCardLanguage(dto.language),
  imageUrl:resolveImageUrl(dto.thumbnail_url),
});

const mapAuctionCard=(dto:AuctionCardResponseDto):CardDto=>({
  id:dto.id,
  name:dto.name,
  marketPrice:0,
  lowPrice:0,
  highPrice:0,
  changeRate:0,
  theme:'gold',
  bidCount:0,
  psaGrade:normalizePsaGrade(dto.psa_grade),
  language:mapCardLanguage(dto.language),
  imageUrl:resolveImageUrl(dto.thumbnail_url),
});

export const mapAuction=(dto:AuctionResponseDto):AuctionDto=>({
  id:dto.id,
  card:mapAuctionCard(dto.card),
  startPrice:dto.start_price,
  currentPrice:dto.current_price,
  bidIncrement:dto.bid_increment,
  bidCount:dto.bid_count,
  startsAt:dto.starts_at,
  endsAt:dto.ends_at,
  status:dto.status,
  myBidStatus:dto.my_bid_status,
  myBidAmount:dto.my_bid_amount,
  version:dto.version,
});
