export type StatisticInsightDto={
  id:'RISING'|'NEW_BIDS'|'ACTIVE';
  title:string;
  value:number;
  changeRate:number|null;
  note:string;
  sort:'BID_COUNT'|'PRICE_HIGH'|'PRICE_LOW'|'CHANGE_HIGH';
};

export type StatisticMarketPointDto={
  date:string;
  averagePrice:number|null;
  bidCount:number;
};

export type StatisticRankingDto={
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

export type StatisticMarketDto={
  marketSummary:{
    monthlyWinningPriceTotal:number;
    monthlyEndedAuctionCount:number;
    monthlyBidCount:number;
    monthlyHighestPrice:number;
  };
  marketHistory:StatisticMarketPointDto[];
};

export type StatisticPriceMoversDto={
  periodDays:number;
  gainers:StatisticRankingDto[];
  losers:StatisticRankingDto[];
};
