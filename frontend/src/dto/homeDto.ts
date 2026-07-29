export type HomeInsightDto={
  id:'RISING'|'NEW_BIDS'|'ACTIVE';
  title:string;
  value:number;
  changeRate:number|null;
  note:string;
  sort:'BID_COUNT'|'PRICE_HIGH'|'PRICE_LOW'|'CHANGE_HIGH';
};

export type HomeMarketPointDto={
  date:string;
  averagePrice:number;
  bidCount:number;
};

export type HomeRankingDto={
  cardId:number;
  name:string;
  price:number;
  changeRate:number;
  theme:string;
  bidCount:number;
  imageUrl:string|null;
  currentDate:string;
  previousDate:string;
  priceHistory:Array<{
    date:string;
    price:number;
  }>;
};

export type HomeMarketDto={
  marketSummary:{
    monthlyWinningPriceTotal:number;
    monthlyEndedAuctionCount:number;
    monthlyBidCount:number;
    monthlyHighestPrice:number;
  };
  marketHistory:HomeMarketPointDto[];
};

export type HomeTopGainersDto={
  topGainersTitle:string;
  topGainers:HomeRankingDto[];
};

export type HomePriceMoversDto={
  periodDays:number;
  gainers:HomeRankingDto[];
  losers:HomeRankingDto[];
};
