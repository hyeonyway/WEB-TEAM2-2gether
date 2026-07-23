import {ChevronRight} from 'lucide-react';

export default function SellPageHeader(){
  return <div className="sell-heading">
    <div>
      <small>SELL YOUR CARD</small>
      <h1>판매 등록</h1>
      <p>카드 정보부터 경매 조건까지 단계별로 입력해 주세요.</p>
    </div>
    <a href="/auction">경매 목록 <ChevronRight/></a>
  </div>;
}
