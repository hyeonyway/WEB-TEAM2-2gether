import {useState} from 'react';
import type {CardTheme} from '../../../dto/auctionDto';

export default function CardArtwork({theme,imageUrl,name}:{theme:CardTheme;imageUrl?:string|null;name?:string}){
  const[failed,setFailed]=useState(false);
  if(imageUrl&&!failed)return <img className="catalog-card-image" src={imageUrl} alt={name??'카드 이미지'} loading="lazy" onError={()=>setFailed(true)}/>;
  return <div className={`mini-card ${theme}`}><i>HP 70</i><span>●</span><small>POKÉMON</small></div>;
}
