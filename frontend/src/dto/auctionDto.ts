export type CardTheme='gold'|'water'|'dark'|'multi'|'sketch';
export type AuctionStatus='OPEN'|'ENDING'|'ENDED'|'CANCELLED'|'FAILED';
export type MyBidStatus='LEADING'|'OUTBID'|'NONE';
export type AuctionSort='LATEST'|'BID_COUNT'|'PRICE_HIGH'|'PRICE_LOW'|'CHANGE_HIGH'|'ENDING_SOON';
export type CardSort='PRICE'|'FAVORITE'|'REGISTERED'|'NAME';

export type CardListRequestDto={
  keyword:string;
  psaGrade:string|null;
  sort?:CardSort;
};

export type AuctionListRequestDto=Omit<CardListRequestDto,'sort'>&{
  sort:AuctionSort;
  size:number;
};

export type CardResponseDto={
  id:number;
  name:string;
  set_name:string;
  market_price:number;
  low_price?:number;
  high_price?:number;
  change_rate:number;
  theme:string;
  bid_count:number;
  psa_grade:string;
  language:string;
  thumbnail_url?:string|null;
};

export type AuctionCardResponseDto={
  id:number;
  name:string;
  set_name:string;
  psa_grade:string|null;
  language:string;
  thumbnail_url:string|null;
};

export type CardPricePointResponseDto={
  date:string;
  average_price:number|null;
  ended_auction_count:number;
  change_rate:number;
  weekly_change_rate:number;
  monthly_change_rate:number;
};

export type CardDetailResponseDto={
  id:number;
  name:string;
  set_name:string;
  rarity:string|null;
  market_price:number;
  low_price:number;
  high_price:number;
  average_price:number;
  change_rate:number;
  weekly_change_rate:number;
  monthly_change_rate:number;
  bid_count:number;
  ended_auction_count:number;
  active_auction_count:number;
  wishlist_count:number;
  psa_grade:string|null;
  language:string;
  image_url:string|null;
  history:CardPricePointResponseDto[];
};

export type AuctionResponseDto={
  id:number;
  card:AuctionCardResponseDto;
  seller:{
    id:number;
    nickname:string;
    trade_count:number;
    trust_score:number;
  };
  start_price:number;
  current_price:number;
  bid_increment:number;
  minimum_bid:number;
  bid_count:number;
  buy_now_price?:number|null;
  starts_at:string;
  ends_at:string;
  status:AuctionStatus;
  my_bid_status:MyBidStatus;
  my_bid_amount:number|null;
};

export type AuctionPhotoResponseDto={
  id:number;
  url:string;
  order:number;
  representative:boolean;
};

export type AuctionDetailResponseDto=AuctionResponseDto&{
  description:string;
  seller_memo:string|null;
  seller_grade:string|null;
  shipping_fee:number;
  buy_now_price:number|null;
  photos:AuctionPhotoResponseDto[];
  psa_certification:{
    certification_number:string|null;
    grade:string|null;
    population:number|null;
    verified:boolean;
  }|null;
};

export type BidSummaryResponseDto={
  id:number|null;
  amount:number;
  bidder_alias:string;
  is_highest:boolean;
  created_at:string;
};

export type BidContextResponseDto={
  eventId?:number;
  auction_id:number;
  status:AuctionStatus;
  current_price:number;
  minimum_bid:number;
  bid_increment:number;
  my_bid_status:MyBidStatus;
  my_bid_amount:number|null;
  wallet:{
    available_balance:number;
    frozen_balance:number;
  };
  recent_bids:BidSummaryResponseDto[];
};

export type MockAuctionResponseDto=Pick<
  AuctionResponseDto,
  'id'|'start_price'|'current_price'|'bid_increment'|'bid_count'|'ends_at'|'status'|'my_bid_status'|'my_bid_amount'
>&{
  card_id:number;
};

export type PageResponseDto<T>={
  content:T[];
  page:number;
  size:number;
  total_elements:number;
  has_next:boolean;
};

export type CursorPageResponseDto<T>={
  content:T[];
  next_cursor:string|null;
  has_next:boolean;
};

export type BidCreateResponseDto={
  bid:{
    id:number|null;
    amount:number;
    status:'LEADING'|'OUTBID'|'WON'|'CANCELLED';
    created_at:string;
    event_id?:string|null;
  };
  auction:{
    id:number;
    current_price:number;
    minimum_bid:number;
    bid_count:number;
    ends_at:string;
  };
  wallet:{
    available_balance:number;
    frozen_balance:number;
  };
  pendingOrder?:{
    auction_id:number;
    status:'PENDING';
    stream_id:string;
  }|null;
};

export type CardDto={
  id:number;
  name:string;
  setName?:string;
  marketPrice:number;
  lowPrice:number;
  highPrice:number;
  changeRate:number;
  theme:CardTheme;
  bidCount:number;
  psaGrade:string;
  language:'JP'|'EN'|'KR';
  imageUrl:string|null;
};

export type AuctionDto={
  eventId?:number;
  id:number;
  sellerId?:number;
  card:CardDto;
  startPrice:number;
  currentPrice:number;
  bidIncrement:number;
  bidCount:number;
  buyNowPrice?:number|null;
  startsAt?:string;
  endsAt:string;
  status:AuctionStatus;
  myBidStatus:MyBidStatus;
  myBidAmount:number|null;
};

export type FailedAuctionResponseDto={
  id:number;
  card_name:string;
  start_price:number;
  closed_at:string;
};

export type FailedAuctionDto={
  id:number;
  cardName:string;
  startPrice:number;
  closedAt:string;
};
