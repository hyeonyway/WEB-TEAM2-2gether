import {queryOptions} from '@tanstack/react-query';
import {fetchHomeInsights,fetchHomeMarket,fetchHomePriceMovers} from '../api/homeApi';

export const homeQueryKeys={
  all:['home'] as const,
  insights:()=>[...homeQueryKeys.all,'insights'] as const,
  market:(days:number)=>[...homeQueryKeys.all,'market',days] as const,
  priceMovers:(limit:number)=>[...homeQueryKeys.all,'price-movers',limit] as const,
};

export const homeQueries={
  insights:()=>queryOptions({
    queryKey:homeQueryKeys.insights(),
    queryFn:fetchHomeInsights,
    staleTime:30_000,
  }),
  market:(days=30)=>queryOptions({
    queryKey:homeQueryKeys.market(days),
    queryFn:()=>fetchHomeMarket(days),
    staleTime:30_000,
  }),
  priceMovers:(limit=5)=>queryOptions({
    queryKey:homeQueryKeys.priceMovers(limit),
    queryFn:()=>fetchHomePriceMovers(limit),
    staleTime:30_000,
  }),
};
