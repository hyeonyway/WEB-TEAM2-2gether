import {queryOptions} from '@tanstack/react-query';
import {fetchHomeOverview} from '../api/homeApi';

export const homeQueryKeys={
  all:['home'] as const,
  overview:()=>[...homeQueryKeys.all,'overview'] as const,
};

export const homeQueries={
  overview:()=>queryOptions({
    queryKey:homeQueryKeys.overview(),
    queryFn:fetchHomeOverview,
    staleTime:30_000,
  }),
};
