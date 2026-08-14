import {useCallback,useEffect,useMemo,useRef,useState} from 'react';
import {useInfiniteQuery,useQueryClient,type InfiniteData} from '@tanstack/react-query';
import {Search} from 'lucide-react';
import {useSearchParams} from 'react-router-dom';
import {AuctionCatalog,AuctionCatalogSkeleton} from './components';
import type {AuctionDto,AuctionListRequestDto,CursorPageResponseDto} from '../../dto/auctionDto';
import {auctionQueries,auctionQueryKeys} from '../../queries/auctionQueries';
import {applyAuctionEvent,sortAuctions} from '../../queries/auctionStreamCache';
import {useAuctionStream} from '../../hooks/useAuctionStream';
import {Header} from '../../components';
import {useDebouncedValue} from '../../hooks/useDebouncedValue';
import {useAuth} from '../../auth/useAuth';

const PAGE_SIZE=12;
const sorts:Array<[string,AuctionListRequestDto['sort']]>= [
  ['최신순','LATEST'],['마감 임박순','ENDING_SOON'],['입찰 수 높은순','BID_COUNT'],['경매가 높은순','PRICE_HIGH'],['경매가 낮은순','PRICE_LOW'],['상승률 높은순','CHANGE_HIGH'],
];
type AuctionListCache=InfiniteData<CursorPageResponseDto<AuctionDto>,string|undefined>;
type EndingRankState={key:string;ranks:Map<number,number>};

export default function AuctionPage(){
  const queryClient=useQueryClient();
  const{status:authStatus}=useAuth();
  const viewerScope=authStatus==='authenticated'?'self':'public';
  const[searchParams,setSearchParams]=useSearchParams();
  const requestedSort=searchParams.get('sort');
  const requestedKeyword=searchParams.get('keyword')??'';
  const initialSort=sorts.some(([,value])=>value===requestedSort)?requestedSort as AuctionListRequestDto['sort']:'LATEST';
  const[query,setQuery]=useState(requestedKeyword);
  const debouncedQuery=useDebouncedValue(query);
  const[grade,setGrade]=useState('');
  const sort=initialSort;
  const listRequest={keyword:debouncedQuery,psaGrade:grade||null,sort,size:PAGE_SIZE};
  const listOptions=auctionQueries.list(listRequest,viewerScope);
  const endingRankState=useRef<EndingRankState>({key:'',ranks:new Map()});

  useEffect(()=>{
    setQuery(requestedKeyword);
  },[requestedKeyword]);
  const{
    data,isPending,error,fetchNextPage,hasNextPage,isFetchingNextPage,isFetchNextPageError,
  }=useInfiniteQuery({...listOptions,enabled:authStatus!=='initializing'});
  const auctions=useMemo(()=>{
    const unique=new Map<number,AuctionDto>();
    data?.pages.flatMap(page=>page.content).forEach(auction=>unique.set(auction.id,auction));
    const loaded=[...unique.values()];
    if(sort!=='ENDING_SOON')return sortAuctions(loaded,sort);

    // 입찰 후 목록을 refetch해도 이미 보던 마감 임박 경매의 상대 순서는 유지한다.
    // 새로 ENDING이 된 경매만 현재 목록의 뒤에 추가한다.
    const rankKey=`${debouncedQuery}|${grade}`;
    if(endingRankState.current.key!==rankKey){
      endingRankState.current={
        key:rankKey,
        ranks:new Map(loaded.filter(auction=>auction.status==='ENDING')
          .sort(()=>Math.random()-.5).map((auction,index)=>[auction.id,index])),
      };
    }
    const ranks=endingRankState.current.ranks;
    let nextRank=ranks.size;
    loaded.filter(auction=>auction.status==='ENDING').forEach(auction=>{
      if(!ranks.has(auction.id))ranks.set(auction.id,nextRank++);
    });
    return sortAuctions(loaded,sort,ranks);
  },[data,debouncedQuery,grade,sort]);
  const[subscriptionAuctionIds,setSubscriptionAuctionIds]=useState<readonly number[]>([]);
  const onSubscriptionAuctionIdsChange=useCallback((auctionIds:readonly number[])=>{
    setSubscriptionAuctionIds(current=>current.join(',')===auctionIds.join(',')?current:auctionIds);
  },[]);
  // 스크롤 중엔 IntersectionObserver가 가시영역을 빠르게 갈아치우므로, 값이 바뀔 때마다
  // 바로 SSE를 재연결하면 재연결이 연속으로 터진다. 디바운스로 스크롤이 멈춘 뒤에만 반영한다.
  const debouncedSubscriptionAuctionIds=useDebouncedValue(subscriptionAuctionIds,400);
  useAuctionStream({
    auctionIds:debouncedSubscriptionAuctionIds,
    onAuctionUpdated:event=>{
      queryClient.setQueryData<AuctionListCache>(listOptions.queryKey,current=>{
        if(!current)return current;
        return {
          ...current,
          // 서버 커서 경계를 유지하기 위해 로드된 항목만 제자리에서 갱신한다.
          pages:current.pages.map(page=>({...page,content:applyAuctionEvent(page.content,event)})),
        };
      });
    },
    onReconnected:()=>{
      void queryClient.invalidateQueries({queryKey:auctionQueryKeys.lists()});
    },
  });
  const loadMoreRef=useRef<HTMLDivElement>(null);

  useEffect(()=>{
    const target=loadMoreRef.current;
    if(!target||!hasNextPage||isFetchNextPageError)return;
    const observer=new IntersectionObserver(entries=>{
      if(entries.some(entry=>entry.isIntersecting)&&!isFetchingNextPage){
        void fetchNextPage();
      }
    },{rootMargin:'240px 0px'});
    observer.observe(target);
    return()=>observer.disconnect();
  },[fetchNextPage,hasNextPage,isFetchingNextPage,isFetchNextPageError]);

  return <div className="cards-page enhanced-cards"><Header/><main>
    <div className="card-page-title"><h2>카드 경매</h2><span>전체 경매</span></div>
    <label className="card-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="경매 카드 검색..."/></label>
    <div className="card-toolbar"><div>{sorts.map(([label,value])=><button key={value} className={sort===value?'active':''} onClick={()=>setSearchParams(current=>{current.set('sort',value);return current;},{replace:true})}>{label}</button>)}</div>
      <label className="grade-filter"><select value={grade} onChange={event=>setGrade(event.target.value)} aria-label="PSA 등급 필터">
        <option value="">PSA 등급</option>{Array.from({length:10},(_,index)=>10-index).map(value=><option key={value} value={value}>PSA {value}</option>)}
      </select></label>
    </div>
    {isPending?<AuctionCatalogSkeleton/>:error&&!data?<p className="form-error">경매 정보를 불러오지 못했습니다.</p>:<>
      <p className="catalog-count">총 {(data?.pages[0]?.total_elements??0).toLocaleString()}개 / {auctions.length.toLocaleString()}개 표시 중</p>
      <AuctionCatalog auctions={auctions} onSubscriptionAuctionIdsChange={onSubscriptionAuctionIdsChange}/>
      {isFetchingNextPage&&<AuctionCatalogSkeleton count={3} label="다음 경매 목록을 불러오는 중"/>}
      {isFetchNextPageError&&<button className="auction-list-retry" type="button" onClick={()=>void queryClient.resetQueries({queryKey:listOptions.queryKey,exact:true})}>목록 새로고침</button>}
      <div className="auction-scroll-sentinel" ref={loadMoreRef} aria-hidden="true"/>
    </>}
  </main></div>;
}
