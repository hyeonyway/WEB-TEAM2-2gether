import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {getDebugUserId} from '../api/debugAuthStorage';
import {useAuth} from '../auth/useAuth';
import {showToast} from '../components/Toast';
import {wishlistMutations} from '../queries/wishlistMutations';
import {wishlistQueries} from '../queries/wishlistQueries';

export function useWishlist(){
  const {status}=useAuth();
  const debugUserId=getDebugUserId();
  const isLoggedIn=status==='authenticated'||debugUserId!==null;
  const cacheKey=debugUserId??'self';
  const queryClient=useQueryClient();
  const{data,isLoading}=useQuery({...wishlistQueries.list(cacheKey),enabled:isLoggedIn});
  const favoriteCardIds=(data??[]).map(item=>item.cardId);

  const addMutation=useMutation(wishlistMutations.add(queryClient,cacheKey));
  const removeMutation=useMutation(wishlistMutations.remove(queryClient,cacheKey));
  const isPending=isLoading||addMutation.isPending||removeMutation.isPending;

  const toggleFavorite=(cardId:number)=>{
    if(!isLoggedIn){
      showToast('로그인이 필요합니다');
      return;
    }
    if(isPending)return;
    if(favoriteCardIds.includes(cardId))removeMutation.mutate(cardId);
    else addMutation.mutate(cardId);
  };

  return {favoriteCardIds,toggleFavorite,isPending};
}
