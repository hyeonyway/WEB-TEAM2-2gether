import {useEffect,useState} from 'react';
import {useQuery,useQueryClient} from '@tanstack/react-query';
import {Search} from 'lucide-react';
import {useSearchParams} from 'react-router-dom';
import {AuctionCatalog,AuctionCatalogSkeleton,AuctionPagination} from './components';
import type {AuctionDto,AuctionListRequestDto,PageResponseDto} from '../../dto/auctionDto';
import {applyAuctionListEvent,auctionQueries} from '../../queries/auctionQueries';
import {useAuctionStream} from '../../hooks/useAuctionStream';
import {Header} from '../../components';
import {useDebouncedValue} from '../../hooks/useDebouncedValue';
import {useAuth} from '../../auth/useAuth';

const PAGE_SIZE=12;
const sorts:Array<[string,AuctionListRequestDto['sort']]>= [
  ['입찰 수 높은순','BID_COUNT'],['경매가 높은순','PRICE_HIGH'],['경매가 낮은순','PRICE_LOW'],['상승률 높은순','CHANGE_HIGH'],
];
export default function AuctionPage(){
  const queryClient=useQueryClient();
  const{status:authStatus}=useAuth();
  const viewerScope=authStatus==='authenticated'?'self':'public';
  const[searchParams]=useSearchParams();
  const requestedSort=searchParams.get('sort');
  const requestedKeyword=searchParams.get('keyword')??'';
  const initialSort=sorts.some(([,value])=>value===requestedSort)?requestedSort as AuctionListRequestDto['sort']:'BID_COUNT';
  const[query,setQuery]=useState(requestedKeyword);
  const debouncedQuery=useDebouncedValue(query);
  const[grade,setGrade]=useState('');
  const[sort,setSort]=useState<AuctionListRequestDto['sort']>(initialSort);
  const[page,setPage]=useState(0);
  const listRequest={keyword:debouncedQuery,psaGrade:grade||null,sort,page,size:PAGE_SIZE};

  useEffect(()=>{
    setQuery(requestedKeyword);
    setPage(0);
  },[requestedKeyword]);
  useEffect(()=>setPage(0),[debouncedQuery]);
  useAuctionStream({
    onAuctionUpdated:event=>{
      queryClient.setQueryData<PageResponseDto<AuctionDto>>(
        auctionQueries.list(listRequest,viewerScope).queryKey,
        current=>applyAuctionListEvent(current,event,listRequest),
      );
    },
  });
  const{data,isPending,error}=useQuery(auctionQueries.list(listRequest,viewerScope));
  const auctions=data?.content??[];

  useEffect(()=>{
    if(!data)return;
    const lastPage=Math.max(0,Math.ceil(data.total_elements/PAGE_SIZE)-1);
    if(page>lastPage)setPage(lastPage);
  },[data,page]);

  return <div className="cards-page enhanced-cards"><Header/><main>
    <div className="card-page-title"><h2>카드 경매</h2><span>전체 경매</span></div>
    <label className="card-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="경매 카드 검색..."/></label>
    <div className="card-toolbar"><div>{sorts.map(([label,value])=><button key={value} className={sort===value?'active':''} onClick={()=>{setSort(value);setPage(0)}}>{label}</button>)}</div>
      <label className="grade-filter"><select value={grade} onChange={event=>{setGrade(event.target.value);setPage(0)}} aria-label="PSA 등급 필터">
        <option value="">PSA 등급</option>{Array.from({length:10},(_,index)=>10-index).map(value=><option key={value} value={value}>PSA {value}</option>)}
      </select></label>
    </div>
    {isPending?<AuctionCatalogSkeleton/>:error?<p className="form-error">경매 정보를 불러오지 못했습니다.</p>:<>
      <p className="catalog-count">전체 {(data?.total_elements??0).toLocaleString()}개 · {auctions.length.toLocaleString()}개 표시 중</p>
      <AuctionCatalog auctions={auctions}/>
      <AuctionPagination
        page={data?.page??page}
        size={data?.size??PAGE_SIZE}
        totalElements={data?.total_elements??0}
        onPageChange={setPage}
      />
    </>}
  </main></div>;
}
