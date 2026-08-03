import {useCallback,useEffect,useRef,useState} from 'react';
import type {NotificationDto} from '../dto/notificationDto';

const AUTO_DISMISS_MS=30_000;

export function useNotificationToasts(){
  const[toasts,setToasts]=useState<NotificationDto[]>([]);
  const timersRef=useRef(new Map<number,ReturnType<typeof setTimeout>>());

  const dismiss=useCallback((id:number)=>{
    setToasts(current=>current.filter(toast=>toast.id!==id));
    const timer=timersRef.current.get(id);
    if(timer){
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
  },[]);

  const push=useCallback((notification:NotificationDto)=>{
    setToasts(current=>current.some(toast=>toast.id===notification.id)?current:[...current,notification]);
    const timer=setTimeout(()=>dismiss(notification.id),AUTO_DISMISS_MS);
    timersRef.current.set(notification.id,timer);
  },[dismiss]);

  useEffect(()=>{
    const timers=timersRef.current;
    return()=>{
      timers.forEach(timer=>clearTimeout(timer));
      timers.clear();
    };
  },[]);

  return {toasts,push,dismiss};
}
