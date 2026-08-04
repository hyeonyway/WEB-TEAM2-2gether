import {useEffect,useMemo,useState} from 'react';
import {ChevronLeft,ChevronRight} from 'lucide-react';
import type {AuctionPhotoResponseDto} from '../../dto/auctionDto';

type AuctionImageGalleryProps={
  cardName:string;
  cardImage:string|null;
  photos:AuctionPhotoResponseDto[];
};

export default function AuctionImageGallery({cardName,cardImage,photos}:AuctionImageGalleryProps){
  const images=useMemo(()=>{
    const orderedPhotos=[...photos].sort((left,right)=>left.order-right.order);
    return [...new Set([cardImage,...orderedPhotos.map(photo=>photo.url)].filter((url):url is string=>Boolean(url)))];
  },[cardImage,photos]);
  const imageKey=images.join('|');
  const[selectedIndex,setSelectedIndex]=useState(0);

  useEffect(()=>setSelectedIndex(0),[imageKey]);

  if(images.length===0){
    return <div className="auction-gallery-empty" aria-label="등록된 이미지 없음"/>;
  }

  const move=(offset:number)=>{
    setSelectedIndex(current=>(current+offset+images.length)%images.length);
  };

  return <div className="auction-image-gallery">
    <div className="auction-gallery-stage">
      <img src={images[selectedIndex]} alt={`${cardName} 이미지 ${selectedIndex+1} / ${images.length}`}/>
      {images.length>1&&<>
        <button type="button" className="auction-gallery-nav previous" aria-label="이전 이미지" onClick={()=>move(-1)}><ChevronLeft/></button>
        <button type="button" className="auction-gallery-nav next" aria-label="다음 이미지" onClick={()=>move(1)}><ChevronRight/></button>
      </>}
    </div>
    <div className="auction-gallery-thumbnails" aria-label="경매 이미지 목록">
      {images.map((image,index)=><button
        type="button"
        key={`${image}-${index}`}
        className={index===selectedIndex?'active':''}
        aria-label={`${index+1}번째 이미지 보기`}
        aria-current={index===selectedIndex?'true':undefined}
        onClick={()=>setSelectedIndex(index)}
      ><img src={image} alt=""/></button>)}
    </div>
  </div>;
}
