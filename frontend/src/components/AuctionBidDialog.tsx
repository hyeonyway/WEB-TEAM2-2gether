import {CheckCircle2,Wallet} from 'lucide-react';
import {useState} from 'react';
import type {AuctionDto} from '../dto/auctionDto';

const recentBids=[[137000,'2시간 전'],[132000,'7시간 전'],[128000,'8시간 전'],[122000,'9시간 전'],[119000,'11시간 전']];

export default function AuctionBidDialog({auction,onClose}:{auction:AuctionDto;onClose:()=>void}){
  const wallet=850000;
  const minimum=auction.currentPrice+1000;
  const[amount,setAmount]=useState<number|string>(minimum);
  const amountValue=Number(amount);
  const belowMinimum=amount===''||amountValue<minimum;
  const insufficient=amountValue>wallet;
  const leading=auction.myBidStatus==='LEADING';

  return <div className="bid-backdrop" onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
    <section className="bid-dialog" role="dialog" aria-modal="true" aria-label={`${auction.card.name} 입찰`}>
      <button className="bid-close" onClick={onClose} aria-label="닫기">×</button>
      <small>실시간 카드 경매</small><h2>입찰하기</h2><p className="bid-card-name">{auction.card.name}</p>
      {leading&&<div className="bid-leading-notice"><CheckCircle2/><span><b>현재 최고가 입찰 중입니다.</b><small>입찰 현황은 확인할 수 있지만 추가 입찰은 제한됩니다.</small></span></div>}
      <div className="bid-wallet"><span><Wallet/>전자지갑 포인트</span><strong>{wallet.toLocaleString()}P</strong></div>
      <div className="bid-current"><span>현재 경매가<b>{auction.currentPrice.toLocaleString()}원</b></span><span>최소 입찰가<b>{minimum.toLocaleString()}원</b></span></div>
      <h3>입찰가 선택</h3>
      <div className="bid-options">{[minimum,minimum+4000,minimum+9000].map(value=><button key={value} className={amountValue===value?'active':''} disabled={leading} onClick={()=>setAmount(value)}>{value.toLocaleString()}원</button>)}</div>
      <label>직접 입력<div className={belowMinimum?'invalid':''}><input disabled={leading} type="number" step="1000" value={amount} onChange={event=>setAmount(event.target.value)}/><span>원</span></div>{belowMinimum&&<small className="bid-minimum-error">최소 입찰가 {minimum.toLocaleString()}원 이상 입력해 주세요.</small>}</label>
      <section className="bid-history"><div className="bid-history-head"><h3>경매 입찰 내역</h3><span>최근 5건</span></div><div className="bid-history-list">{recentBids.map(([price,time],index)=><div className="bid-history-row" key={`${price}-${time}`}><span><b>PSA {auction.card.psaGrade}</b><small>{index===0?'최고 입찰':'입찰 완료'}</small></span><strong>{price.toLocaleString()}원</strong><time>{time}</time></div>)}</div></section>
      <div className="bid-balance"><span>입찰 후 잔여 포인트</span><b>{Math.max(0,wallet-(amountValue||0)).toLocaleString()}P</b></div>
      {insufficient&&<p className="bid-error">전자지갑 포인트가 부족합니다.</p>}
      <button className="bid-submit" disabled={belowMinimum||insufficient||leading} onClick={onClose}>{leading?'현재 최고가 입찰 중':`${amountValue.toLocaleString()}원 입찰하기`}</button>
    </section>
  </div>;
}
