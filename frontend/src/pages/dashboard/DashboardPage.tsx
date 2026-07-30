import {useQuery,useQueryClient} from '@tanstack/react-query';
import {ChevronRight,Search} from 'lucide-react';
import {useState} from 'react';
import {Header} from '../../components';
import type {ParticipatingAuctionSort,RecentWinSort} from '../../api/dashboardApi';
import {dashboardQueries,dashboardQueryKey} from '../../queries/dashboardQueries';
import {useAuctionStream} from '../../hooks/useAuctionStream';
import AuctionCatalog from '../auction/components/AuctionCatalog';

const sections=[
  ['participating','참여 중인 경매','현재 참여 중인 경매를 확인하세요.'],
  ['recent-wins','최근 나의 낙찰','최근 낙찰받은 카드와 낙찰가를 확인하세요.'],
] as const;

type SectionId=(typeof sections)[number][0];
export default function DashboardPage(){
  const[active,setActive]=useState<SectionId>('participating');
  const[query,setQuery]=useState('');
  const[participatingSort,setParticipatingSort]=useState<ParticipatingAuctionSort>('ENDING_SOON');
  const[recentWinSort,setRecentWinSort]=useState<RecentWinSort>('LATEST');
  const queryClient=useQueryClient();
  useAuctionStream({
    enabled:active==='participating',
    onAuctionUpdated:()=>void queryClient.invalidateQueries({
      queryKey:[...dashboardQueryKey,'participating-auctions'],
    }),
  });
  const dashboard=useQuery(
    active==='participating'
      ? dashboardQueries.participating(participatingSort)
      : dashboardQueries.recentWins(recentWinSort),
  );
  const section=sections.find(([id])=>id===active)!;
  const auctions=dashboard.data??[];
  const normalizedQuery=query.trim().toLowerCase();
  const visible=auctions.filter(auction=>
    auction.card.name.toLowerCase().includes(normalizedQuery),
  );

  return <div className="cards-mypage standalone-dashboard enhanced-cards"><Header/><main>
    <div className="cards-dash-title">
      <div><small>MY PAGE</small><h1>경매 대시보드</h1><p>내 카드 경매 현황을 빠르게 확인하세요.</p></div>
      <a className="dashboard-all-auctions" href="/auction">전체 카드 경매 <ChevronRight/></a>
    </div>
    <label className="card-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="내 경매 카드 검색..."/></label>
    <div className="dashboard-filters" role="tablist" aria-label="대시보드 목록 필터">
      {sections.map(([id,title])=><button key={id} role="tab" aria-selected={active===id} className={active===id?'active':''} onClick={()=>setActive(id)}>{title}</button>)}
    </div>
    {active==='participating'&&<div className="dashboard-sort-filters" role="group" aria-label="참여 중인 경매 정렬">
      <button type="button" className={participatingSort==='ENDING_SOON'?'active':''} onClick={()=>setParticipatingSort('ENDING_SOON')}>마감 임박순</button>
      <button type="button" className={participatingSort==='PRICE_HIGH'?'active':''} onClick={()=>setParticipatingSort('PRICE_HIGH')}>경매가 높은순</button>
    </div>}
    {active==='recent-wins'&&<div className="dashboard-sort-filters" role="group" aria-label="최근 낙찰 정렬">
      <button type="button" className={recentWinSort==='LATEST'?'active':''} onClick={()=>setRecentWinSort('LATEST')}>최신순</button>
      <button type="button" className={recentWinSort==='OLDEST'?'active':''} onClick={()=>setRecentWinSort('OLDEST')}>오래된 순</button>
      <button type="button" className={recentWinSort==='PRICE_HIGH'?'active':''} onClick={()=>setRecentWinSort('PRICE_HIGH')}>가격순</button>
    </div>}
    {!dashboard.isPending&&!dashboard.isError&&
      <p className="catalog-count">
        전체 {auctions.length.toLocaleString()}개{normalizedQuery&&` · ${visible.length.toLocaleString()}개 표시 중`}
      </p>}
    <section className={`cards-dash-section ${active==='participating'?'participating-section':''}`}>
      <div className="cards-dash-section-head"><div><h2>{section[1]}</h2><p>{section[2]}</p></div></div>
      {dashboard.isPending
        ? <DashboardAuctionSkeleton/>
        : dashboard.isError
          ? <div className="filter-empty"><b>대시보드를 불러오지 못했습니다.</b><button type="button" onClick={()=>dashboard.refetch()}>다시 시도</button></div>
          : <AuctionCatalog auctions={visible}/>}
    </section>
  </main></div>;
}

function DashboardAuctionSkeleton(){
  return <section className="card-grid dashboard-auction-skeleton" aria-label="참여 경매를 불러오는 중" aria-busy="true">
    {Array.from({length:6},(_,index)=><article className="card-tile skeleton-card" key={index}>
      <div className="dashboard-skeleton-art skeleton-pulse"/>
      <div className="dashboard-skeleton-content">
        <i className="skeleton-line short skeleton-pulse"/>
        <i className="skeleton-line title skeleton-pulse"/>
        <i className="skeleton-line medium skeleton-pulse"/>
        <div className="dashboard-skeleton-values">
          {Array.from({length:4},(_,valueIndex)=><i className="skeleton-pulse" key={valueIndex}/>)}
        </div>
        <div className="dashboard-skeleton-actions"><i className="skeleton-pulse"/><i className="skeleton-pulse"/></div>
      </div>
    </article>)}
  </section>;
}
