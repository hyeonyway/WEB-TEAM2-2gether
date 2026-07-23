import {Bookmark} from 'lucide-react';
import {useState} from 'react';

const storageKey='favorite-auction-ids';
const readFavorites=():number[]=>{
  try{return JSON.parse(localStorage.getItem(storageKey)??'[]')}catch{return[]}
};

export default function FavoriteButton({auctionId}:{auctionId:number}){
  const[active,setActive]=useState(()=>readFavorites().includes(auctionId));
  const toggle=()=>{
    const current=readFavorites();
    const next=current.includes(auctionId)?current.filter(id=>id!==auctionId):[...current,auctionId];
    localStorage.setItem(storageKey,JSON.stringify(next));
    setActive(next.includes(auctionId));
  };
  return <button className={`favorite-button${active?' active':''}`} type="button" aria-label={active?'경매 찜 해제':'경매 찜하기'} aria-pressed={active} onClick={toggle}><Bookmark/></button>;
}
