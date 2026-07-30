import {queryOptions} from '@tanstack/react-query';
import {fetchStatisticInsights,fetchStatisticMarket,fetchStatisticPriceMovers} from '../api/statisticApi';

export const statisticQueryKeys={
  all:['statistic'] as const,
  insights:()=>[...statisticQueryKeys.all,'insights'] as const,
  market:(days:number)=>[...statisticQueryKeys.all,'market',days] as const,
  priceMovers:(limit:number)=>[...statisticQueryKeys.all,'price-movers',limit] as const,
};

export const statisticQueries={
  insights:()=>queryOptions({
    queryKey:statisticQueryKeys.insights(),
    queryFn:fetchStatisticInsights,
    staleTime:30_000,
  }),
  market:(days=30)=>queryOptions({
    queryKey:statisticQueryKeys.market(days),
    queryFn:()=>fetchStatisticMarket(days),
    staleTime:30_000,
  }),
  priceMovers:(limit=5)=>queryOptions({
    queryKey:statisticQueryKeys.priceMovers(limit),
    queryFn:()=>fetchStatisticPriceMovers(limit),
    staleTime:30_000,
  }),
};
