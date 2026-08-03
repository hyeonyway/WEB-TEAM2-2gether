import {queryOptions} from '@tanstack/react-query';
import {fetchWishlistCards,fetchWishlists} from '../api/wishlistApi';

export const wishlistQueryKeys={
  all:['wishlists'] as const,
  list:(userId:string)=>[...wishlistQueryKeys.all,userId] as const,
  cards:(userId:string)=>[...wishlistQueryKeys.list(userId),'cards'] as const,
};

export const wishlistQueries={
  list:(userId:string)=>queryOptions({
    queryKey:wishlistQueryKeys.list(userId),
    queryFn:()=>fetchWishlists(),
    staleTime:60_000,
    refetchOnMount:'always',
  }),
  cards:(userId:string)=>queryOptions({
    queryKey:wishlistQueryKeys.cards(userId),
    queryFn:()=>fetchWishlistCards(),
    staleTime:60_000,
    refetchOnMount:'always',
  }),
};
