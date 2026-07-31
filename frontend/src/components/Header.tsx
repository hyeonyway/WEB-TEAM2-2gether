// @ts-nocheck
import{useMutation,useQueryClient}from'@tanstack/react-query';
import React,{useState,useSyncExternalStore}from'react';
import{Wallet}from'lucide-react';
import{useLocation,useNavigate}from'react-router-dom';
import{getAccessToken,subscribeAccessToken}from'../api/accessTokenStore';
import{useAuthGate}from'../auth/useAuthGate';
import{authMutations}from'../queries/authMutations';
import{useWalletBalance}from'../queries/walletQueries';
import{walletQueryKeys}from'../queries/walletQueryKeys';
import AuthModal from './auth/AuthModal';
import NotificationBell from'./NotificationBell';
import{showToast}from'./Toast';
import WalletChargeDialog from'./wallet/WalletChargeDialog';

const mainNavigation=[
  {href:'/',label:'홈'},
  {href:'/cards',label:'카드 시세'},
  {href:'/auction',label:'카드 경매'},
  {href:'/sell',label:'판매 등록'},
  {href:'/dashboard',label:'나의 대시보드'},
];

const isActivePath=(href:string,path:string)=>href==='/'?path==='/':path===href||path.startsWith(`${href}/`);

export default function Header(){
  const[chargeOpen,setChargeOpen]=useState(false);
  const[authOpen,setAuthOpen]=useState(false);
  const{pathname:path}=useLocation();
  const navigate=useNavigate();
  const authGate=useAuthGate();
  const walletQuery=useWalletBalance();
  const accessToken=useSyncExternalStore(subscribeAccessToken,getAccessToken,getAccessToken);
  const queryClient=useQueryClient();
  const logoutMutation=useMutation({
    ...authMutations.logout(),
    onSettled:()=>{
      queryClient.removeQueries({queryKey:['auth']});
      queryClient.removeQueries({queryKey:walletQueryKeys.all});
      navigate('/');
    },
  });

  const protectedAuthOpen=authGate.authModalOpen;
  const wallet=walletQuery.data?.totalBalance;
  const walletControl=authGate.status==='authenticated'
    ?walletQuery.isPending
      ?<div className="header-wallet header-wallet-skeleton" role="status" aria-label="전자지갑 잔액 불러오는 중"/>
      :walletQuery.isError
        ?<button type="button" className="header-wallet header-wallet-error" aria-label="전자지갑 잔액 다시 시도" onClick={()=>void walletQuery.refetch()}><Wallet/><b>다시 시도</b></button>
      :wallet!==undefined
        ?<button className="header-wallet" onClick={()=>setChargeOpen(true)}><Wallet/><span><small>내 전자지갑</small><strong>{wallet.toLocaleString()}P</strong></span><b>충전하기</b></button>
        :null
    :null;
  return <><header><div className="head-inner"><a className="logo" href="/" aria-label="홈으로 이동">KREAM</a><nav className="header-main-nav" aria-label="주요 메뉴">{mainNavigation.map(item=><a key={item.href} href={item.href} className={isActivePath(item.href,path)?'active':''} aria-current={isActivePath(item.href,path)?'page':undefined} onClick={event=>{if(item.href==='/dashboard'&&authGate.status!=='authenticated'){event.preventDefault();showToast('로그인이 필요합니다')}}}>{item.label}</a>)}</nav><div className="head-account-actions">{walletControl}<NotificationBell/><nav className="header-account-nav" aria-label="계정 메뉴"><a href="/mypage" className={isActivePath('/mypage',path)?'active':''} onClick={event=>{if(!authGate.requestAccess('/mypage'))event.preventDefault()}}>마이페이지</a>{accessToken?<button type="button" disabled={logoutMutation.isPending} onClick={()=>logoutMutation.mutate()}>{logoutMutation.isPending?'로그아웃 중...':'로그아웃'}</button>:<button type="button" onClick={()=>setAuthOpen(true)}>로그인</button>}</nav></div></div></header>{chargeOpen&&wallet!==undefined&&<WalletChargeDialog balance={wallet} onClose={()=>setChargeOpen(false)}/>}<AuthModal open={authOpen||protectedAuthOpen} onClose={()=>{setAuthOpen(false);if(protectedAuthOpen)authGate.cancelAuthentication()}} onLoginSuccess={()=>{setAuthOpen(false);if(protectedAuthOpen)authGate.completeAuthentication()}}/></>;
}
