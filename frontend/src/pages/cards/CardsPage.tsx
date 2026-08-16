import {useEffect,useRef,useState} from 'react';
import {useInfiniteQuery,useQuery} from '@tanstack/react-query';
import {useSearchParams} from 'react-router-dom';
import {Search} from 'lucide-react';
import {CardCatalog,CardCatalogSkeleton} from './components';
import {cardQueries} from '../../queries/auctionQueries';
import {Header} from '../../components';
import {useDebouncedValue} from '../../hooks/useDebouncedValue';
import type {CardSort} from '../../dto/auctionDto';
import {useWishlist} from '../../hooks/useWishlist';
import {wishlistQueries} from '../../queries/wishlistQueries';
import {useAuthGate} from '../../auth/useAuthGate';

export default function CardsPage(){
  const[searchParams,setSearchParams]=useSearchParams();
  const initialSort=searchParams.get('sort');
  const[query,setQuery]=useState(()=>searchParams.get('keyword')??'');
  const[grade,setGrade]=useState(()=>searchParams.get('psaGrade')??'');
  const[sort,setSort]=useState<CardSort>(()=>
    initialSort==='PRICE'||initialSort==='FAVORITE'||initialSort==='NAME'||initialSort==='REGISTERED'
      ?initialSort
      :'REGISTERED',
  );
  const[favoriteOnly,setFavoriteOnly]=useState(()=>searchParams.get('favoriteOnly')==='true');
  const{cacheKey,isLoggedIn}=useWishlist();
  const authGate=useAuthGate();
  const debouncedQuery=useDebouncedValue(query);
  const loadMoreRef=useRef<HTMLDivElement>(null);
  const cardQuery=cardQueries.infiniteList({keyword:debouncedQuery,psaGrade:grade||null,sort});
  const{data,isPending,error,hasNextPage,isFetchingNextPage,fetchNextPage}=useInfiniteQuery(cardQuery);
  const cards=data?.pages.flatMap(page=>page.content)??[];
  const favoriteCardsQuery=useQuery({
    ...wishlistQueries.cards(cacheKey),
    enabled:favoriteOnly&&isLoggedIn,
  });
  const visibleCards=favoriteOnly?(favoriteCardsQuery.data??[]):cards;
  const catalogPending=favoriteOnly
    ?isLoggedIn&&favoriteCardsQuery.isPending
    :isPending;
  const catalogError=favoriteOnly?favoriteCardsQuery.error:error;
  const totalElements=data?.pages[0]?.total_elements??0;

  const updateFilters=(next:{keyword?:string;psaGrade?:string;sort?:CardSort;favoriteOnly?:boolean})=>{
    setSearchParams(current=>{
      const params=new URLSearchParams(current);
      const keyword=next.keyword??query;
      const psaGrade=next.psaGrade??grade;
      const nextSort=next.sort??sort;
      const nextFavoriteOnly=next.favoriteOnly??favoriteOnly;
      if(keyword)params.set('keyword',keyword);else params.delete('keyword');
      if(psaGrade)params.set('psaGrade',psaGrade);else params.delete('psaGrade');
      if(nextSort==='REGISTERED')params.delete('sort');else params.set('sort',nextSort);
      if(nextFavoriteOnly)params.set('favoriteOnly','true');else params.delete('favoriteOnly');
      return params;
    },{replace:true});
  };

  useEffect(()=>{
    const target=loadMoreRef.current;
    if(!target||favoriteOnly)return;
    const observer=new IntersectionObserver(entries=>{
      if(entries[0]?.isIntersecting&&hasNextPage&&!isFetchingNextPage)void fetchNextPage();
    },{rootMargin:'300px'});
    observer.observe(target);
    return()=>observer.disconnect();
  },[favoriteOnly,fetchNextPage,hasNextPage,isFetchingNextPage]);

  return <div className="card-catalog-page">
    <Header/>
    <main>
      <div className="catalog-heading">
        <div><small>POKÉMON TCG</small><h1>전체 카드 정보</h1><p>카드의 시세와 거래 정보를 확인하세요.</p></div>
      </div>
      <div className="catalog-controls">
        <label className="card-search"><Search/><input value={query} onChange={event=>{setQuery(event.target.value);updateFilters({keyword:event.target.value})}} placeholder="카드명 검색..."/></label>
      </div>
      <div className="card-toolbar">
        <div>
          <button className={!favoriteOnly&&sort==='REGISTERED'?'active':''} onClick={()=>{setFavoriteOnly(false);setSort('REGISTERED');updateFilters({favoriteOnly:false,sort:'REGISTERED'})}}>등록순</button>
          <button className={!favoriteOnly&&sort==='PRICE'?'active':''} onClick={()=>{setFavoriteOnly(false);setSort('PRICE');updateFilters({favoriteOnly:false,sort:'PRICE'})}}>가격순</button>
          <button className={!favoriteOnly&&sort==='FAVORITE'?'active':''} onClick={()=>{setFavoriteOnly(false);setSort('FAVORITE');updateFilters({favoriteOnly:false,sort:'FAVORITE'})}}>찜 많은 순</button>
          <button className={favoriteOnly?'active':''} onClick={()=>{
            if(!authGate.requestNavigation())return;
            setFavoriteOnly(true);
            updateFilters({favoriteOnly:true});
          }}>나의 찜</button>
          <label className="grade-filter"><span>PSA 등급</span><select value={grade} onChange={event=>{setGrade(event.target.value);updateFilters({psaGrade:event.target.value})}}>
            <option value="">전체 등급</option>
            {Array.from({length:10},(_,index)=>10-index).map(value=><option key={value} value={value}>PSA {value}</option>)}
          </select></label>
        </div>
      </div>
      <p className="catalog-count">{favoriteOnly?`나의 찜 ${visibleCards.length.toLocaleString()}개`:`전체 ${totalElements.toLocaleString()}개 · ${cards.length.toLocaleString()}개 표시 중`}</p>
      {catalogPending?<CardCatalogSkeleton/>:catalogError?<p className="form-error">카드 정보를 불러오지 못했습니다.</p>:<CardCatalog cards={visibleCards}/>}
      {!favoriteOnly&&isFetchingNextPage&&<CardCatalogSkeleton count={3}/>}
      <div ref={loadMoreRef} className="catalog-count" aria-live="polite">
        {favoriteOnly||isFetchingNextPage?'':hasNextPage?'아래로 스크롤하면 더 불러옵니다.':cards.length?'모든 카드를 불러왔습니다.':''}
      </div>
    </main>
  </div>;
}
