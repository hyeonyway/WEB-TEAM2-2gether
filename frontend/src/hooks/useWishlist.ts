import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {getDebugUserId} from '../api/debugAuthStorage';
import {showToast} from '../components/Toast';
import {wishlistMutations} from '../queries/wishlistMutations';
import {wishlistQueries} from '../queries/wishlistQueries';

export function useWishlist(){
  const userId=getDebugUserId();
  const queryClient=useQueryClient();
  const{data}=useQuery({...wishlistQueries.list(userId??''),enabled:userId!==null});
  const favoriteCardIds=(data??[]).map(item=>item.cardId);

  const addMutation=useMutation(wishlistMutations.add(queryClient,userId??''));
  const removeMutation=useMutation(wishlistMutations.remove(queryClient,userId??''));

  const toggleFavorite=(cardId:number)=>{
    if(!userId){
      showToast('로그인이 필요합니다');
      return;
    }
    if(favoriteCardIds.includes(cardId))removeMutation.mutate(cardId);
    else addMutation.mutate(cardId);
  };

  return {favoriteCardIds,toggleFavorite};
}
