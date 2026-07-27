export type CardTheme='gold'|'water'|'dark'|'multi'|'sketch';
export type AuctionStatus='OPEN'|'ENDED';
export type MyBidStatus='LEADING'|'OUTBID'|'NONE';
export type AuctionSort='BID_COUNT'|'PRICE_HIGH'|'PRICE_LOW'|'CHANGE_HIGH';

export type CardListRequestDto={
  keyword:string;
  psaGrade:number|null;
};

export type AuctionListRequestDto=CardListRequestDto&{
  sort:AuctionSort;
};

export type CardResponseDto={
  id:number;
  name:string;
  market_price:number;
  change_rate:number;
  theme:string;
  bid_count:number;
  psa_grade:number;
  language:string;
  thumbnail_url?:string|null;
};

export type CardPricePointResponseDto={
  date:string;
  average_price:number;
  trade_count:number;
};

export type CardDetailResponseDto={
  id:number;
  name:string;
  set_name:string;
  card_number:string|null;
  rarity:string|null;
  market_price:number;
  low_price:number;
  high_price:number;
  average_price:number;
  change_rate:number;
  weekly_change_rate:number;
  monthly_change_rate:number;
  trade_count:number;
  bid_count:number;
  active_auction_count:number;
  favorite_count:number;
  psa_grade:number|null;
  language:string;
  image_url:string|null;
  history:CardPricePointResponseDto[];
};

export type AuctionResponseDto={
  id:number;
  card:CardResponseDto;
  start_price?:number;
  current_price:number;
  bid_increment?:number;
  bid_count:number;
  ends_at:string;
  status:AuctionStatus;
  my_bid_status?:MyBidStatus;
  my_bid_amount?:number|null;
};

export type MockAuctionResponseDto=Omit<AuctionResponseDto,'card'>&{
  card_id:number;
};

export type PageResponseDto<T>={
  content:T[];
  page:number;
  size:number;
  total_elements:number;
  has_next:boolean;
};

export type CardDto={
  id:number;
  name:string;
  marketPrice:number;
  changeRate:number;
  theme:CardTheme;
  bidCount:number;
  psaGrade:number;
  language:'JP'|'EN'|'KR';
  imageUrl:string|null;
};

export type AuctionDto={
  id:number;
  card:CardDto;
  startPrice:number;
  currentPrice:number;
  bidIncrement:number;
  bidCount:number;
  endsAt:string;
  status:AuctionStatus;
  myBidStatus:MyBidStatus;
  myBidAmount:number|null;
};
