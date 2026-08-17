import {useEffect,useRef} from 'react';
import type {InfiniteData} from '@tanstack/react-query';
import {useQueryClient} from '@tanstack/react-query';
import {isMockApiEnabled} from '../api/mockApiConfig';
import {fetchWalletBalance} from '../api/walletApi';
import {getSessionUserId} from '../auth/session/sessionAuthStore';
import {revalidateSession} from '../auth/session/sessionRevalidation';
import type {NotificationDto,NotificationPageDto} from '../dto/notificationDto';
import type {WalletBalanceDto} from '../dto/walletDto';
import {applyNotificationCreated} from '../queries/notificationStreamCache';
import {notificationQueryKeys} from '../queries/notificationQueries';
import {walletQueryKeys} from '../queries/walletQueryKeys';
import {notificationDedupKey} from '../utils/notificationKey';
import {nextSseReconnectDelayMs,shouldRevalidateSession} from './sseReconnectPolicy';

const NOTIFICATION_CREATED_EVENT='notification-created';
const WALLET_STATE_CHANGED_EVENT='wallet-state-changed';

type WalletSsePayload={
  wallet_version:number;
  total_balance:number;
  frozen_balance:number;
  available_balance:number;
  updated_at:string;
};

function streamUrl():string{
  const apiBaseUrl=(import.meta.env.VITE_API_BASE_URL??'').replace(/\/+$/,'');
  return `${apiBaseUrl}/api/me/stream`;
}

function parseWalletPayload(data:string):WalletSsePayload|null{
  try{
    const payload=JSON.parse(data) as Partial<WalletSsePayload>;
    if(!Number.isSafeInteger(payload.wallet_version)||!Number.isSafeInteger(payload.total_balance)
      ||!Number.isSafeInteger(payload.frozen_balance)||!Number.isSafeInteger(payload.available_balance)
      ||typeof payload.updated_at!=='string')return null;
    return payload as WalletSsePayload;
  }catch{return null;}
}

function parseNotificationPayload(data:string):NotificationDto|null{
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

type UseMeStreamOptions={
  enabled?:boolean;
  onNotificationCreated?:(notification:NotificationDto)=>void;
};

export function useMeStream({
  enabled=true,
  onNotificationCreated,
}:UseMeStreamOptions={}):void{
  const queryClient=useQueryClient();
  const onNotificationCreatedRef=useRef(onNotificationCreated);
  onNotificationCreatedRef.current=onNotificationCreated;
  const seenNotificationKeysRef=useRef(new Set<string>());
  const highestWalletVersion=useRef(-1);

  useEffect(()=>{
    if(!enabled||isMockApiEnabled()){
      highestWalletVersion.current=-1;
      return;
    }

    let eventSource:EventSource|null=null;
    let reconnectTimer:ReturnType<typeof setTimeout>|null=null;
    let stopped=false;
    let opened=false;
    let consecutiveFailures=0;

    const handleWalletStateChanged=(event:Event)=>{
      const payload=parseWalletPayload((event as MessageEvent<string>).data);
      if(!payload||payload.wallet_version<=highestWalletVersion.current)return;
      highestWalletVersion.current=payload.wallet_version;
      queryClient.setQueryData<WalletBalanceDto>(walletQueryKeys.balance(),{
        totalBalance:payload.total_balance,
        frozenBalance:payload.frozen_balance,
        availableBalance:payload.available_balance,
        walletVersion:payload.wallet_version,
      });
    };

    const handleNotificationCreated=(event:Event)=>{
      const notification=parseNotificationPayload((event as MessageEvent<string>).data);
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

    const recoverWalletBalance=async()=>{
      const versionBeforeRecovery=highestWalletVersion.current;
      try{
        const balance=await fetchWalletBalance();
        if(!stopped&&highestWalletVersion.current===versionBeforeRecovery){
          queryClient.setQueryData<WalletBalanceDto>(walletQueryKeys.balance(),balance);
          if(balance.walletVersion!==undefined){
            highestWalletVersion.current=Math.max(highestWalletVersion.current,balance.walletVersion);
          }
        }
      }catch{
        // 다음 SSE snapshot 또는 기존 화면 조회가 최신 상태를 복구한다.
      }
    };

    const reconnect=()=>{
      if(stopped||reconnectTimer)return;
      const delay=nextSseReconnectDelayMs(consecutiveFailures);
      reconnectTimer=setTimeout(()=>{reconnectTimer=null;void connect();},delay);
    };

    const handleFailure=()=>{
      consecutiveFailures+=1;
      if(shouldRevalidateSession(consecutiveFailures))void revalidateSession();
      reconnect();
    };

    const detach=()=>{
      eventSource?.removeEventListener(WALLET_STATE_CHANGED_EVENT,handleWalletStateChanged);
      eventSource?.removeEventListener(NOTIFICATION_CREATED_EVENT,handleNotificationCreated);
    };

    const connect=async()=>{
      if(stopped)return;
      if(!getSessionUserId()){
        reconnect();
        return;
      }
      if(stopped)return;
      try{
        eventSource=new EventSource(streamUrl(),{withCredentials:true});
        eventSource.addEventListener(WALLET_STATE_CHANGED_EVENT,handleWalletStateChanged);
        eventSource.addEventListener(NOTIFICATION_CREATED_EVENT,handleNotificationCreated);
        eventSource.onopen=()=>{
          consecutiveFailures=0;
          if(opened){
            void recoverWalletBalance();
            void queryClient.invalidateQueries({queryKey:notificationQueryKeys.all});
          }
          opened=true;
        };
        eventSource.onerror=()=>{
          detach();
          eventSource?.close();
          eventSource=null;
          handleFailure();
        };
      }catch{handleFailure();}
    };

    void connect();

    return()=>{
      stopped=true;
      if(reconnectTimer)clearTimeout(reconnectTimer);
      detach();
      eventSource?.close();
    };
  },[enabled,queryClient]);
}
