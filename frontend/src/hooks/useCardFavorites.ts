import {useEffect,useState} from 'react';

const STORAGE_KEY='favorite-card-ids';
const CHANGE_EVENT='card-favorites-change';

function readFavoriteCardIds():number[]{
  try{
    const value=JSON.parse(localStorage.getItem(STORAGE_KEY)??'[]');
    return Array.isArray(value)?value.filter(Number.isInteger):[];
  }catch{
    return [];
  }
}

export function useCardFavorites(){
  const[favoriteCardIds,setFavoriteCardIds]=useState(readFavoriteCardIds);

  useEffect(()=>{
    const sync=()=>setFavoriteCardIds(readFavoriteCardIds());
    window.addEventListener('storage',sync);
    window.addEventListener(CHANGE_EVENT,sync);
    return()=>{
      window.removeEventListener('storage',sync);
      window.removeEventListener(CHANGE_EVENT,sync);
    };
  },[]);

  const toggleFavorite=(cardId:number)=>{
    const current=readFavoriteCardIds();
    const next=current.includes(cardId)?current.filter(id=>id!==cardId):[...current,cardId];
    localStorage.setItem(STORAGE_KEY,JSON.stringify(next));
    window.dispatchEvent(new Event(CHANGE_EVENT));
  };

  return {favoriteCardIds,toggleFavorite};
}
