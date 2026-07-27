import {Bookmark} from 'lucide-react';
import {useCardFavorites} from '../../../hooks/useCardFavorites';

export default function CardFavoriteButton({cardId}:{cardId:number}){
  const{favoriteCardIds,toggleFavorite}=useCardFavorites();
  const active=favoriteCardIds.includes(cardId);

  return <button
    className={`favorite-button${active?' active':''}`}
    type="button"
    aria-label={active?'카드 찜 해제':'카드 찜하기'}
    aria-pressed={active}
    onClick={event=>{
      event.preventDefault();
      event.stopPropagation();
      toggleFavorite(cardId);
    }}
  ><Bookmark/></button>;
}
