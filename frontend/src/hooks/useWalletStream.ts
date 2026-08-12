import {useEffect,useRef} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {isSessionAuthMode} from '../auth/authMode';
import {isMockApiEnabled} from '../api/mockApiConfig';
import {issueSseTicket} from '../api/notificationTicketApi';
import {fetchWalletBalance} from '../api/walletApi';
import type {WalletBalanceDto} from '../dto/walletDto';
import {walletQueryKeys} from '../queries/walletQueryKeys';

const RECONNECT_DELAY_MS=2_000;

type WalletSsePayload={
  wallet_version:number;
  total_balance:number;
  frozen_balance:number;
  available_balance:number;
  updated_at:string;
};

function streamUrl(ticket?:string){
  const apiBaseUrl=(import.meta.env.VITE_API_BASE_URL??'').replace(/\/+$/,'');
  return `${apiBaseUrl}/api/me/wallet/stream${ticket?`?ticket=${encodeURIComponent(ticket)}`:''}`;
}

function parsePayload(data:string):WalletSsePayload|null{
  try{
    const payload=JSON.parse(data) as Partial<WalletSsePayload>;
    if(!Number.isSafeInteger(payload.wallet_version)||!Number.isSafeInteger(payload.total_balance)
      ||!Number.isSafeInteger(payload.frozen_balance)||!Number.isSafeInteger(payload.available_balance)
      ||typeof payload.updated_at!=='string')return null;
    return payload as WalletSsePayload;
  }catch{return null;}
}

export function useWalletStream(enabled:boolean){
  const queryClient=useQueryClient();
  const highestVersion=useRef(-1);

  useEffect(()=>{
    if(!enabled||isMockApiEnabled()){
      highestVersion.current=-1;
      return;
    }
    let eventSource:EventSource|null=null;
    let reconnectTimer:ReturnType<typeof setTimeout>|null=null;
    let stopped=false;
    let opened=false;
    const reconnect=()=>{
      if(stopped||reconnectTimer)return;
      reconnectTimer=setTimeout(()=>{reconnectTimer=null;void connect();},RECONNECT_DELAY_MS);
    };
    const receive=(event:Event)=>{
      const payload=parsePayload((event as MessageEvent<string>).data);
      if(!payload||payload.wallet_version<=highestVersion.current)return;
      highestVersion.current=payload.wallet_version;
      queryClient.setQueryData<WalletBalanceDto>(walletQueryKeys.balance(),{
        totalBalance:payload.total_balance,
        frozenBalance:payload.frozen_balance,
        availableBalance:payload.available_balance,
        walletVersion:payload.wallet_version,
      });
    };
    const recoverBalance=async()=>{
      const versionBeforeRecovery=highestVersion.current;
      try{
        const balance=await fetchWalletBalance();
        if(!stopped&&highestVersion.current===versionBeforeRecovery){
          queryClient.setQueryData<WalletBalanceDto>(walletQueryKeys.balance(),balance);
          if(balance.walletVersion!==undefined){
            highestVersion.current=Math.max(highestVersion.current,balance.walletVersion);
          }
        }
      }catch{
        // 다음 SSE snapshot 또는 기존 화면 조회가 최신 상태를 복구한다.
      }
    };
    const connect=async()=>{
      if(stopped)return;
      try{
        const ticket=isSessionAuthMode()?undefined:(await issueSseTicket()).ticket;
        if(stopped)return;
        eventSource=new EventSource(streamUrl(ticket),isSessionAuthMode()?{withCredentials:true}:undefined);
        eventSource.addEventListener('wallet-state-changed',receive);
        eventSource.onopen=()=>{
          if(opened)void recoverBalance();
          opened=true;
        };
        eventSource.onerror=()=>{
          eventSource?.removeEventListener('wallet-state-changed',receive);
          eventSource?.close();eventSource=null;reconnect();
        };
      }catch{reconnect();}
    };
    void connect();
    return()=>{stopped=true;if(reconnectTimer)clearTimeout(reconnectTimer);eventSource?.close();};
  },[enabled,queryClient]);
}
