import {useEffect,useRef,useState} from 'react';
import {useInfiniteQuery} from '@tanstack/react-query';
import {Search} from 'lucide-react';
import {CardCatalog} from './components';
import {cardQueries} from '../../queries/auctionQueries';
import {Header} from '../../components';
import {useDebouncedValue} from '../../hooks/useDebouncedValue';

export default function CardsPage(){
  const[query,setQuery]=useState('');
  const[grade,setGrade]=useState(0);
  const debouncedQuery=useDebouncedValue(query);
  const loadMoreRef=useRef<HTMLDivElement>(null);
  const cardQuery=cardQueries.infiniteList({keyword:debouncedQuery,psaGrade:grade||null});
  const{data,isPending,error,hasNextPage,isFetchingNextPage,fetchNextPage}=useInfiniteQuery(cardQuery);
  const cards=data?.pages.flatMap(page=>page.content)??[];
  const totalElements=data?.pages[0]?.total_elements??0;

  useEffect(()=>{
    const target=loadMoreRef.current;
    if(!target)return;
    const observer=new IntersectionObserver(entries=>{
      if(entries[0]?.isIntersecting&&hasNextPage&&!isFetchingNextPage)void fetchNextPage();
    },{rootMargin:'300px'});
    observer.observe(target);
    return()=>observer.disconnect();
  },[fetchNextPage,hasNextPage,isFetchingNextPage]);

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
      <p className="catalog-count">전체 {totalElements.toLocaleString()}개 · {cards.length.toLocaleString()}개 표시 중</p>
      {isPending?<p className="catalog-count">불러오는 중…</p>:error?<p className="form-error">카드 정보를 불러오지 못했습니다.</p>:<CardCatalog cards={cards}/>}
      <div ref={loadMoreRef} className="catalog-count" aria-live="polite">
        {isFetchingNextPage?'다음 카드를 불러오는 중…':hasNextPage?'아래로 스크롤하면 더 불러옵니다.':cards.length?'모든 카드를 불러왔습니다.':''}
      </div>
    </main>
  </div>;
}
