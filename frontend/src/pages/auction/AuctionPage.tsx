import {useState} from 'react';
import {useQuery,useQueryClient} from '@tanstack/react-query';
import {Search} from 'lucide-react';
import {AuctionCatalog} from './components';
import type {AuctionListRequestDto} from '../../dto/auctionDto';
import {auctionQueries,auctionQueryKeys} from '../../queries/auctionQueries';
import {useAuctionStream} from '../../hooks/useAuctionStream';
import {Header} from '../../components';
import {useDebouncedValue} from '../../hooks/useDebouncedValue';

const sorts:Array<[string,AuctionListRequestDto['sort']]>= [
  ['입찰 수 높은순','BID_COUNT'],['경매가 높은순','PRICE_HIGH'],['경매가 낮은순','PRICE_LOW'],['상승률 높은순','CHANGE_HIGH'],
];
export default function AuctionPage(){
  const queryClient=useQueryClient();
  useAuctionStream({
    onAuctionUpdated:()=>void queryClient.invalidateQueries({
      queryKey:auctionQueryKeys.all,
    }),
  });
  const requestedSort=new URLSearchParams(window.location.search).get('sort');
  const initialSort=sorts.some(([,value])=>value===requestedSort)?requestedSort as AuctionListRequestDto['sort']:'BID_COUNT';
  const[query,setQuery]=useState('');
  const debouncedQuery=useDebouncedValue(query);
  const[grade,setGrade]=useState('');
  const[sort,setSort]=useState<AuctionListRequestDto['sort']>(initialSort);
  const{data:auctions=[],isPending,error}=useQuery(auctionQueries.list({keyword:debouncedQuery,psaGrade:grade||null,sort}));

  return <div className="cards-page enhanced-cards"><Header/><main>
    <div className="card-page-title"><h2>카드 경매</h2><span>전체 경매</span></div>
    <label className="card-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="경매 카드 검색..."/></label>
    <div className="card-toolbar"><div>{sorts.map(([label,value])=><button key={value} className={sort===value?'active':''} onClick={()=>setSort(value)}>{label}</button>)}</div>
      <label className="grade-filter"><select value={grade} onChange={event=>setGrade(event.target.value)} aria-label="PSA 등급 필터">
        <option value="">PSA 등급</option>{Array.from({length:10},(_,index)=>10-index).map(value=><option key={value} value={value}>PSA {value}</option>)}
      </select></label>
    </div>
    {isPending?<p className="catalog-count">불러오는 중…</p>:error?<p className="form-error">경매 정보를 불러오지 못했습니다.</p>:<AuctionCatalog auctions={auctions}/>}
  </main></div>;
}
