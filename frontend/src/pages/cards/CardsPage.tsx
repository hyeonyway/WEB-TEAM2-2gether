import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Search} from 'lucide-react';
import {CardCatalog} from './components';
import {cardQueries} from '../../queries/auctionQueries';
import {Header} from '../../components';

export default function CardsPage(){
  const[query,setQuery]=useState('');
  const[grade,setGrade]=useState(0);
  const{data:cards=[],isPending,error}=useQuery(cardQueries.list({keyword:query,psaGrade:grade||null}));

  return <div className="card-catalog-page">
    <Header/>
    <main>
      <div className="catalog-heading">
        <div><small>POKÉMON TCG</small><h1>전체 카드 정보</h1><p>카드의 시세와 거래 정보를 확인하세요.</p></div>
      </div>
      <div className="catalog-controls">
        <label className="card-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="카드명 검색..."/></label>
        <label className="grade-filter"><span>PSA 등급</span><select value={grade} onChange={event=>setGrade(Number(event.target.value))}>
          <option value="0">전체 등급</option>
          {Array.from({length:10},(_,index)=>10-index).map(value=><option key={value} value={value}>PSA {value}</option>)}
        </select></label>
      </div>
      <p className="catalog-count">전체 {cards.length}개</p>
      {isPending?<p className="catalog-count">불러오는 중…</p>:error?<p className="form-error">카드 정보를 불러오지 못했습니다.</p>:<CardCatalog cards={cards}/>}
    </main>
  </div>;
}
