import {Bookmark} from 'lucide-react';
import {useWishlist} from '../../../hooks/useWishlist';

export default function CardFavoriteButton({cardId}:{cardId:number}){
  const{favoriteCardIds,toggleFavorite,isPending}=useWishlist();
  const active=favoriteCardIds.includes(cardId);

  return <button
    className={`favorite-button${active?' active':''}`}
    type="button"
    disabled={isPending}
    aria-label={active?'카드 찜 해제':'카드 찜하기'}
    aria-pressed={active}
    onClick={event=>{
      event.preventDefault();
      event.stopPropagation();
      toggleFavorite(cardId);
    }}
  ><Bookmark/></button>;
}
