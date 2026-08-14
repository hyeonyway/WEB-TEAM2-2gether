import {useSyncExternalStore} from 'react';
import {createPortal} from 'react-dom';

const TOAST_EVENT='app-toast';
const TOAST_DURATION_MS=2400;

type ToastDetail={id:number;message:string;dedupeKey?:string};

let nextToastId=0;
let toasts:ToastDetail[]=[];
const listeners=new Set<()=>void>();

function emitChange(){
  listeners.forEach(listener=>listener());
}

function subscribe(listener:()=>void){
  listeners.add(listener);
  return()=>listeners.delete(listener);
}

function getSnapshot(){
  return toasts;
}

export function showToast(message:string,dedupeKey?:string):void{
  if(dedupeKey&&toasts.some(toast=>toast.dedupeKey===dedupeKey))return;
  nextToastId+=1;
  const toast={id:nextToastId,message,dedupeKey};
  toasts=[...toasts,toast];
  emitChange();
  window.dispatchEvent(new CustomEvent<ToastDetail>(TOAST_EVENT,{detail:toast}));
  setTimeout(()=>{
    toasts=toasts.filter(current=>current.id!==toast.id);
    emitChange();
  },TOAST_DURATION_MS);
}

export default function ToastContainer(){
  const currentToasts=useSyncExternalStore(subscribe,getSnapshot,getSnapshot);

  if(!currentToasts.length)return null;
  return createPortal(<div className="toast-stack" aria-live="polite">
    {currentToasts.map(toast=><div className="toast" key={toast.id}>{toast.message}</div>)}
  </div>,document.body);
}
