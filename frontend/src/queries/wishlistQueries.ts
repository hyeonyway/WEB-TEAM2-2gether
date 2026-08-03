import {queryOptions} from '@tanstack/react-query';
import {fetchWishlists} from '../api/wishlistApi';

export const wishlistQueryKeys={
  all:['wishlists'] as const,
  list:(userId:string)=>[...wishlistQueryKeys.all,userId] as const,
};

export const wishlistQueries={
  list:(userId:string)=>queryOptions({
    queryKey:wishlistQueryKeys.list(userId),
    queryFn:()=>fetchWishlists(),
    staleTime:60_000,
    refetchOnMount:'always',
  }),
};
