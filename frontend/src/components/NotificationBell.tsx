import {useEffect,useRef,useState} from 'react';
import {Bell} from 'lucide-react';
import {useNavigate} from 'react-router-dom';
import {showAuthRequiredToast} from '../auth/useAuthGate';
import {useNotifications} from '../hooks/useNotifications';
import {formatLocalDate} from '../utils/dateTime';
import {getNotificationPath} from '../utils/notificationNavigation';
import {showToast} from './Toast';
import type {NotificationDto} from '../dto/notificationDto';

function formatRelativeTime(iso:string):string{
  const diffMs=Date.now()-new Date(iso).getTime();
  const minute=60_000,hour=60*minute,day=24*hour;
  if(diffMs<minute)return'방금 전';
  if(diffMs<hour)return`${Math.floor(diffMs/minute)}분 전`;
  if(diffMs<day)return`${Math.floor(diffMs/hour)}시간 전`;
  if(diffMs<7*day)return`${Math.floor(diffMs/day)}일 전`;
  return formatLocalDate(iso);
}

function NotificationItem({notification,onRead,onNavigate}:{notification:NotificationDto;onRead:(notification:NotificationDto)=>void;onNavigate:(notification:NotificationDto)=>void}){
  return <li className={`notification-item ${notification.isRead?'read':'unread'}`}>
    <button type="button" className="notification-item-body" onClick={()=>onRead(notification)}>
      <p>{notification.message}</p>
      <span>{formatRelativeTime(notification.createdAt)}</span>
    </button>
    <button type="button" className="notification-item-move" onClick={()=>onNavigate(notification)}>이동</button>
  </li>;
}

export default function NotificationBell(){
  const[isOpen,setIsOpen]=useState(false);
  const[unreadOnly,setUnreadOnly]=useState(false);
  const loadMoreRef=useRef<HTMLLIElement>(null);
  const dialogRef=useRef<HTMLElement>(null);
  const previousFocusRef=useRef<HTMLElement|null>(null);
  const navigate=useNavigate();
  const{isLoggedIn,notifications,unreadCount,isLoading,isError,hasNextPage,isFetchingNextPage,fetchNextPage,refetchAll,markAsRead,markAllAsRead}=useNotifications(unreadOnly,isOpen);

  useEffect(()=>{
    if(isOpen)refetchAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  },[isOpen]);

  useEffect(()=>{
    const target=loadMoreRef.current;
    if(!isOpen||!target)return;
    const observer=new IntersectionObserver(entries=>{
      if(entries[0]?.isIntersecting&&hasNextPage&&!isFetchingNextPage)fetchNextPage();
    },{rootMargin:'200px'});
    observer.observe(target);
    return()=>observer.disconnect();
  },[isOpen,hasNextPage,isFetchingNextPage,fetchNextPage]);

  useEffect(()=>{
    if(!isOpen)return;
    const handleKeyDown=(event:KeyboardEvent)=>{
      if(event.key==='Escape'){
        setIsOpen(false);
        return;
      }
      if(event.key!=='Tab')return;
      const dialog=dialogRef.current;
      if(!dialog)return;
      const focusableElements=Array.from(dialog.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ));
      const first=focusableElements[0];
      const last=focusableElements.at(-1);
      if(!first||!last){
        event.preventDefault();
        dialog.focus();
        return;
      }
      if(event.shiftKey&&(document.activeElement===first||!dialog.contains(document.activeElement))){
        event.preventDefault();
        last.focus();
      }else if(!event.shiftKey&&(document.activeElement===last||!dialog.contains(document.activeElement))){
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener('keydown',handleKeyDown);
    return()=>window.removeEventListener('keydown',handleKeyDown);
  },[isOpen]);

  useEffect(()=>{
    if(!isOpen)return;
    previousFocusRef.current=document.activeElement instanceof HTMLElement?document.activeElement:null;
    dialogRef.current?.focus();
    return()=>{
      previousFocusRef.current?.focus();
      previousFocusRef.current=null;
    };
  },[isOpen]);

  const handleTrigger=()=>{
    if(!isLoggedIn){
      showAuthRequiredToast();
      return;
    }
    setIsOpen(current=>!current);
  };

  const handleNavigate=(notification:NotificationDto)=>{
    markAsRead(notification);
    setIsOpen(false);
    navigate(getNotificationPath(notification));
  };

  return <div className="notification-bell">
    <button className="notification-bell-trigger" aria-label="알림" onClick={handleTrigger}>
      <Bell/>
      {unreadCount>0&&<span className="notification-badge">{unreadCount>99?'99+':unreadCount}</span>}
    </button>
    {isOpen&&<div className="notification-backdrop" onMouseDown={event=>event.target===event.currentTarget&&setIsOpen(false)}>
      <section ref={dialogRef} className="notification-drawer" role="dialog" aria-modal="true" aria-label="알림 목록" tabIndex={-1}>
        <div className="notification-drawer-header">
          <h2>알림</h2>
          <button className="notification-drawer-close" onClick={()=>setIsOpen(false)} aria-label="닫기">×</button>
        </div>
        <div className="notification-toggle">
          <button className={!unreadOnly?'active':''} onClick={()=>setUnreadOnly(false)}>전체</button>
          <button className={unreadOnly?'active':''} onClick={()=>setUnreadOnly(true)}>안읽음</button>
        </div>
        <ul className="notification-list">
          {isLoading
            ?<li className="notification-empty">불러오는 중...</li>
            :isError
              ?<li className="notification-empty">알림을 불러오지 못했습니다. <button type="button" className="notification-retry" onClick={refetchAll}>다시 시도</button></li>
              :notifications.length===0
                ?<li className="notification-empty">{unreadOnly?'안읽은 알림이 없습니다.':'알림이 없습니다.'}</li>
                :notifications.map(notification=><NotificationItem key={notification.id} notification={notification} onRead={markAsRead} onNavigate={handleNavigate}/>)}
          <li ref={loadMoreRef} className="notification-load-more" aria-live="polite">{isFetchingNextPage?'불러오는 중...':''}</li>
        </ul>
        <div className="notification-drawer-footer">
          <button onClick={()=>markAllAsRead()} disabled={unreadCount===0}>전체 읽음</button>
        </div>
      </section>
    </div>}
  </div>;
}
