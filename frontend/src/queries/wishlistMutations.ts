import type {QueryClient} from '@tanstack/react-query';
import {mutationOptions} from '@tanstack/react-query';
import {addWishlist,removeWishlist} from '../api/wishlistApi';
import type {WishlistDto} from '../dto/wishlistDto';
import {cardQueryKeys} from './auctionQueries';
import {wishlistQueryKeys} from './wishlistQueries';

function optimisticToggle(
  queryClient:QueryClient,
  userId:string,
  cardId:number,
  active:boolean,
){
  const queryKey=wishlistQueryKeys.list(userId);
  const previous=queryClient.getQueryData<WishlistDto[]>(queryKey);
  queryClient.setQueryData<WishlistDto[]>(queryKey,current=>{
    const list=current??[];
    return active
      ?[...list,{id:cardId,cardId}]
      :list.filter(item=>item.cardId!==cardId);
  });
  return previous;
}

function settleWishlist(queryClient:QueryClient,userId:string){
  void queryClient.invalidateQueries({queryKey:wishlistQueryKeys.list(userId)});
  void queryClient.invalidateQueries({queryKey:cardQueryKeys.all});
}

export const wishlistMutations={
  add:(queryClient:QueryClient,userId:string)=>mutationOptions({
    mutationKey:['wishlists','add',userId],
    mutationFn:(cardId:number)=>addWishlist(userId,cardId),
    onMutate:async(cardId:number)=>{
      await queryClient.cancelQueries({queryKey:wishlistQueryKeys.list(userId)});
      return {previous:optimisticToggle(queryClient,userId,cardId,true)};
    },
    onError:(_error,_cardId,context)=>{
      queryClient.setQueryData(wishlistQueryKeys.list(userId),context?.previous);
    },
    onSettled:()=>settleWishlist(queryClient,userId),
  }),
  remove:(queryClient:QueryClient,userId:string)=>mutationOptions({
    mutationKey:['wishlists','remove',userId],
    mutationFn:(cardId:number)=>removeWishlist(userId,cardId),
    onMutate:async(cardId:number)=>{
      await queryClient.cancelQueries({queryKey:wishlistQueryKeys.list(userId)});
      return {previous:optimisticToggle(queryClient,userId,cardId,false)};
    },
    onError:(_error,_cardId,context)=>{
      queryClient.setQueryData(wishlistQueryKeys.list(userId),context?.previous);
    },
    onSettled:()=>settleWishlist(queryClient,userId),
  }),
};
