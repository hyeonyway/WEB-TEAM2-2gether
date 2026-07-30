import {useEffect,useState} from 'react';

const TOAST_EVENT='app-toast';
const TOAST_DURATION_MS=2400;

type ToastDetail={id:number;message:string};

let nextToastId=0;

export function showToast(message:string):void{
  nextToastId+=1;
  window.dispatchEvent(new CustomEvent<ToastDetail>(TOAST_EVENT,{detail:{id:nextToastId,message}}));
}

export default function ToastContainer(){
  const[toasts,setToasts]=useState<ToastDetail[]>([]);

  useEffect(()=>{
    const onToast=(event:Event)=>{
      const {id,message}=(event as CustomEvent<ToastDetail>).detail;
      setToasts(current=>[...current,{id,message}]);
      setTimeout(()=>setToasts(current=>current.filter(toast=>toast.id!==id)),TOAST_DURATION_MS);
    };
    window.addEventListener(TOAST_EVENT,onToast);
    return()=>window.removeEventListener(TOAST_EVENT,onToast);
  },[]);

  if(!toasts.length)return null;
  return <div className="toast-stack" aria-live="polite">
    {toasts.map(toast=><div className="toast" key={toast.id}>{toast.message}</div>)}
  </div>;
}
