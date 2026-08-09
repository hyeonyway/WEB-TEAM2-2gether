import {useCallback,useEffect,useRef,useState} from 'react';
import type {NotificationDto} from '../dto/notificationDto';

const AUTO_DISMISS_MS=30_000;
const DISMISS_ANIMATION_MS=200;

export type ToastState=NotificationDto&{isDismissing:boolean};

export function useNotificationToasts(){
  const[toasts,setToasts]=useState<ToastState[]>([]);
  const timersRef=useRef(new Map<number,ReturnType<typeof setTimeout>>());

  const remove=useCallback((id:number)=>{
    setToasts(current=>current.filter(toast=>toast.id!==id));
    timersRef.current.delete(id);
  },[]);

  const dismiss=useCallback((id:number)=>{
    const timer=timersRef.current.get(id);
    if(timer)clearTimeout(timer);
    setToasts(current=>current.map(toast=>toast.id===id?{...toast,isDismissing:true}:toast));
    timersRef.current.set(id,setTimeout(()=>remove(id),DISMISS_ANIMATION_MS));
  },[remove]);

  const push=useCallback((notification:NotificationDto)=>{
    setToasts(current=>current.some(toast=>toast.id===notification.id)
      ?current
      :[...current,{...notification,isDismissing:false}]);
    timersRef.current.set(notification.id,setTimeout(()=>dismiss(notification.id),AUTO_DISMISS_MS));
  },[dismiss]);

  const clear=useCallback(()=>{
    timersRef.current.forEach(timer=>clearTimeout(timer));
    timersRef.current.clear();
    setToasts([]);
  },[]);

  useEffect(()=>{
    const timers=timersRef.current;
    return()=>{
      timers.forEach(timer=>clearTimeout(timer));
      timers.clear();
    };
  },[]);

  return {toasts,push,dismiss,clear};
}
