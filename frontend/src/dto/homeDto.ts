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
};

export type HomeMarketDto={
  marketSummary:{
    currentPriceAverage:number;
    dailyChangeRate:number;
    weeklyChangeRate:number;
    monthlyChangeRate:number;
    monthlyBidCount:number;
  };
  marketHistory:HomeMarketPointDto[];
};

export type HomeTopGainersDto={
  topGainersTitle:string;
  topGainers:HomeRankingDto[];
};
