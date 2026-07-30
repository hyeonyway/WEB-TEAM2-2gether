// @ts-nocheck
import React,{useState}from'react';
import{Wallet}from'lucide-react';
import AuthModal from './auth/AuthModal';

function WalletChargeDialog({balance,onClose,onCharge}){const[amount,setAmount]=useState(50000);return <div className="wallet-charge-backdrop" onMouseDown={event=>event.target===event.currentTarget&&onClose()}><section className="wallet-charge-dialog" role="dialog" aria-modal="true" aria-label="전자지갑 포인트 충전"><button className="wallet-charge-close" onClick={onClose} aria-label="닫기">×</button><div className="wallet-charge-title"><span><Wallet/></span><div><small>전자지갑</small><h2>포인트 충전</h2></div></div><div className="wallet-current"><span>현재 보유 포인트</span><strong>{balance.toLocaleString()}P</strong></div><h3>충전 금액</h3><div className="wallet-charge-options">{[50000,100000,300000].map(value=><button key={value} className={amount===value?'active':''} onClick={()=>setAmount(value)}>+{(value/10000).toLocaleString()}만원</button>)}</div><div className="wallet-after"><span>충전 후 포인트</span><b>{(balance+amount).toLocaleString()}P</b></div><button className="wallet-charge-submit" onClick={()=>onCharge(amount)}>{amount.toLocaleString()}P 충전하기</button><p>프로토타입용 간편 충전이며 실제 결제는 진행되지 않습니다.</p></section></div>}

const mainNavigation=[
  {href:'/',label:'홈'},
  {href:'/cards',label:'카드 시세'},
  {href:'/auction',label:'카드 경매'},
  {href:'/sell',label:'판매 등록'},
  {href:'/dashboard',label:'나의 대시보드'},
];

const isActivePath=(href:string,path:string)=>href==='/'?path==='/':path===href||path.startsWith(`${href}/`);

export default function Header(){const[wallet,setWallet]=useState(850000),[chargeOpen,setChargeOpen]=useState(false),[authOpen,setAuthOpen]=useState(false),path=window.location.pathname;return <><header><div className="head-inner"><a className="logo" href="/" aria-label="홈으로 이동">KREAM</a><nav className="header-main-nav" aria-label="주요 메뉴">{mainNavigation.map(item=><a key={item.href} href={item.href} className={isActivePath(item.href,path)?'active':''} aria-current={isActivePath(item.href,path)?'page':undefined}>{item.label}</a>)}</nav><div className="head-account-actions"><button className="header-wallet" onClick={()=>setChargeOpen(true)}><Wallet/><span><small>내 전자지갑</small><strong>{wallet.toLocaleString()}P</strong></span><b>충전하기</b></button><nav className="header-account-nav" aria-label="계정 메뉴"><a href="/mypage" className={isActivePath('/mypage',path)?'active':''}>마이페이지</a><button type="button" onClick={()=>setAuthOpen(true)}>로그인</button></nav></div></div></header>{chargeOpen&&<WalletChargeDialog balance={wallet} onClose={()=>setChargeOpen(false)} onCharge={amount=>{setWallet(value=>value+amount);setChargeOpen(false)}}/>}<AuthModal open={authOpen} onClose={()=>setAuthOpen(false)}/></>}
