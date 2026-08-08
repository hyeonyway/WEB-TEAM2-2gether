import {useEffect,useState} from 'react';

export function useCountdownNow(intervalMs=1000):number{
  const[now,setNow]=useState(()=>Date.now());
  useEffect(()=>{
    const timer=window.setInterval(()=>setNow(Date.now()),intervalMs);
    return()=>window.clearInterval(timer);
  },[intervalMs]);
  return now;
}

export function formatRemaining(endsAt:string,now:number):string{
  const total=Math.max(0,Math.ceil((new Date(endsAt).getTime()-now)/1000));
  if(total===0)return '경매 종료';
  const hours=Math.floor(total/3600);
  const minutes=Math.floor(total%3600/60);
  const seconds=total%60;
  return `${String(hours).padStart(2,'0')}:${String(minutes).padStart(2,'0')}:${String(seconds).padStart(2,'0')}`;
}

export function isAuctionEnded(status:string,remaining:string):boolean{
  return !['OPEN','ENDING'].includes(status)||remaining==='경매 종료';
}
