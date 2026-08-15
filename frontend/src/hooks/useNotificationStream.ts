import {useEffect,useRef} from 'react';
import type {InfiniteData} from '@tanstack/react-query';
import {useQueryClient} from '@tanstack/react-query';
import {getSessionUserId} from '../auth/session/sessionAuthStore';
import {revalidateSession} from '../auth/session/sessionRevalidation';
import {isMockApiEnabled} from '../api/mockApiConfig';
import type {NotificationDto,NotificationPageDto} from '../dto/notificationDto';
import {applyNotificationCreated} from '../queries/notificationStreamCache';
import {notificationQueryKeys} from '../queries/notificationQueries';
import {notificationDedupKey} from '../utils/notificationKey';
import {nextSseReconnectDelayMs,shouldRevalidateSession} from './sseReconnectPolicy';

const NOTIFICATION_CREATED_EVENT='notification-created';

function sessionNotificationStreamUrl():string{
  const apiBaseUrl=(import.meta.env.VITE_API_BASE_URL??'').replace(/\/+$/,'');
  return `${apiBaseUrl}/api/me/notifications/stream`;
}

function parsePayload(data:string):NotificationDto|null{
  try{
    const raw=JSON.parse(data) as Partial<NotificationDto>;
    if(
      typeof raw.id!=='number'
      ||typeof raw.auctionId!=='number'
      ||typeof raw.bidId!=='number'
      ||typeof raw.message!=='string'
      ||typeof raw.isRead!=='boolean'
      ||typeof raw.createdAt!=='string'
    )return null;
    return raw as NotificationDto;
  }catch{
    return null;
  }
}

type UseNotificationStreamOptions={
  enabled?:boolean;
  onNotificationCreated?:(notification:NotificationDto)=>void;
};

export function useNotificationStream({
  enabled=true,
  onNotificationCreated,
}:UseNotificationStreamOptions={}):void{
  const queryClient=useQueryClient();
  const onNotificationCreatedRef=useRef(onNotificationCreated);
  onNotificationCreatedRef.current=onNotificationCreated;
  const seenNotificationKeysRef=useRef(new Set<string>());

  useEffect(()=>{
    if(!enabled||isMockApiEnabled())return;

    let eventSource:EventSource|null=null;
    let reconnectTimer:ReturnType<typeof setTimeout>|null=null;
    let stopped=false;
    let consecutiveFailures=0;

    const handleNotificationCreated=(event:Event)=>{
      const notification=parsePayload((event as MessageEvent<string>).data);
      if(!notification)return;
      const dedupKey=notificationDedupKey(notification);
      if(seenNotificationKeysRef.current.has(dedupKey))return;
      seenNotificationKeysRef.current.add(dedupKey);

      queryClient.setQueryData<InfiniteData<NotificationPageDto>>(
        notificationQueryKeys.list(false),
        current=>applyNotificationCreated(current,notification,false),
      );
      queryClient.setQueryData<InfiniteData<NotificationPageDto>>(
        notificationQueryKeys.list(true),
        current=>applyNotificationCreated(current,notification,true),
      );
      if(!notification.isRead){
        queryClient.setQueryData<number>(
          notificationQueryKeys.unreadCount,
          current=>(current??0)+1,
        );
      }
      onNotificationCreatedRef.current?.(notification);
    };

    const scheduleReconnect=()=>{
      if(stopped||reconnectTimer)return;
      const delay=nextSseReconnectDelayMs(consecutiveFailures);
      reconnectTimer=setTimeout(()=>{
        reconnectTimer=null;
        void connect();
      },delay);
    };

    const handleFailure=()=>{
      consecutiveFailures+=1;
      if(shouldRevalidateSession(consecutiveFailures))void revalidateSession();
      scheduleReconnect();
    };

    const connect=async()=>{
      if(stopped)return;
      if(!getSessionUserId()){
        scheduleReconnect();
        return;
      }
      if(stopped)return;
      eventSource=new EventSource(sessionNotificationStreamUrl(),{withCredentials:true});
      eventSource.addEventListener(NOTIFICATION_CREATED_EVENT,handleNotificationCreated);
      eventSource.onopen=()=>{
        consecutiveFailures=0;
      };
      eventSource.onerror=()=>{
        eventSource?.removeEventListener(NOTIFICATION_CREATED_EVENT,handleNotificationCreated);
        eventSource?.close();
        eventSource=null;
        handleFailure();
      };
    };

    void connect();

    return()=>{
      stopped=true;
      if(reconnectTimer)clearTimeout(reconnectTimer);
      eventSource?.removeEventListener(NOTIFICATION_CREATED_EVENT,handleNotificationCreated);
      eventSource?.close();
    };
  },[enabled,queryClient]);
}
