import {useQuery} from '@tanstack/react-query';
import {useState} from 'react';
import {Link} from 'react-router-dom';
import {fetchStreamRecoveryEvents,fetchStreamRecoveryStatus} from '../../api/streamRecoveryApi';
import {Header} from '../../components';
import {useAuth} from '../../auth/useAuth';
import {HttpError} from '../../api/httpClient';
import './StreamRecoveryPage.css';

export default function StreamRecoveryPage(){
  const {status:authStatus}=useAuth();
  const [eventPage,setEventPage]=useState(0);
  const status=useQuery({queryKey:['admin','stream-recovery','status'],queryFn:fetchStreamRecoveryStatus,enabled:authStatus==='authenticated',refetchInterval:5000});
  const events=useQuery({queryKey:['admin','stream-recovery','events',eventPage],queryFn:()=>fetchStreamRecoveryEvents(eventPage),enabled:authStatus==='authenticated'});
  if(authStatus==='initializing')return <div className="cards-mypage standalone-dashboard"><Header/><main className="stream-recovery-page"><p>인증 상태를 확인하고 있습니다.</p></main></div>;
  if(authStatus==='anonymous')return <div className="cards-mypage standalone-dashboard"><Header/><main className="stream-recovery-page recovery-empty"><small>ADMIN CONSOLE</small><h1>Stream 복구 관리자</h1><p>장애 상태와 복구 대상을 확인하려면 관리자 계정으로 로그인해 주세요.</p><div className="recovery-login-guide"><b>로그인</b><span>오른쪽 상단의 로그인 버튼을 눌러 관리자 계정으로 로그인할 수 있습니다.</span></div><Link className="recovery-home-link" to="/">홈으로 이동</Link></main></div>;
  if(status.isPending)return <div className="cards-mypage standalone-dashboard"><Header/><main className="stream-recovery-page"><p>Stream 복구 상태를 불러오는 중입니다.</p></main></div>;
  const noAdminAccess=status.error instanceof HttpError&&status.error.status===403;
  if(status.isError)return <div className="cards-mypage standalone-dashboard"><Header/><main className="stream-recovery-page recovery-empty"><small>ADMIN CONSOLE</small><h1>{noAdminAccess?'관리자 권한이 필요합니다.':'복구 상태를 불러오지 못했습니다.'}</h1><p>{noAdminAccess?'현재 로그인한 계정에는 Stream 복구 권한이 없습니다.':'잠시 후 다시 시도해 주세요.'}</p><button type="button" onClick={()=>void status.refetch()}>다시 시도</button></main></div>;
  const data=status.data;
  return <div className="cards-mypage standalone-dashboard"><Header/><main className="stream-recovery-page">
    <div className="cards-dash-title"><div><small>ADMIN CONSOLE</small><h1>Redis Stream 복구</h1><p>MySQL projection 지연과 오류를 확인하고 안전한 복구를 준비합니다.</p></div><span className={`recovery-state ${data.paused?'paused':'healthy'}`}>{data.paused?'CONSUMER PAUSED':'CONSUMER RUNNING'}</span></div>
    <section className="recovery-summary" aria-label="projection 상태 요약"><article><small>PENDING</small><strong>{data.pendingCount.toLocaleString()}</strong><span>수신 후 반영 대기 이벤트</span></article><article className={data.errorCount?'danger':''}><small>ERROR</small><strong>{data.errorCount.toLocaleString()}</strong><span>원인 확인이 필요한 이벤트</span></article></section>
    <section className="recovery-target"><div><small>RECOVERY START POINT</small><h2>다음 복구 대상</h2></div><code>{data.firstIncompleteStreamId??'미완료 이벤트가 없습니다.'}</code>{data.firstFailureMessage&&<p className="recovery-error-message">{data.firstFailureMessage}</p>}</section>
    <section className="recovery-event-list"><div className="cards-dash-section-head"><div><h2>미완료 projection 이벤트</h2><p>PENDING과 ERROR 이벤트의 원인과 재시도 이력을 확인합니다.</p></div><span>{events.data?.totalElements.toLocaleString()??0}건</span></div>
      {events.isPending?<div className="filter-empty"><b>이벤트 목록을 불러오는 중...</b></div>:events.isError?<div className="filter-empty"><b>이벤트 목록을 불러오지 못했습니다.</b><button type="button" onClick={()=>void events.refetch()}>다시 시도</button></div>:events.data?.content.length?<><div className="recovery-event-table" role="table" aria-label="미완료 projection 이벤트 목록"><div className="recovery-event-row recovery-event-head" role="row"><span>상태</span><span>Stream ID / 이벤트</span><span>경매</span><span>시도</span><span>오류 원인</span></div>{events.data.content.map(event=><div className="recovery-event-row" role="row" key={event.streamId}><span><b className={event.projectionStatus==='ERROR'?'event-error':'event-pending'}>{event.projectionStatus}</b></span><span><code>{event.streamId}</code><small>{event.eventType}</small></span><span>{event.auctionId??'-'}</span><span>{event.attemptCount}회</span><span title={event.failureMessage??undefined}>{event.failureMessage??'projection 대기 중'}</span></div>)}</div>{events.data.totalPages>1&&<div className="recovery-pagination"><button type="button" disabled={eventPage===0} onClick={()=>setEventPage(page=>page-1)}>이전</button><span>{eventPage+1} / {events.data.totalPages}</span><button type="button" disabled={eventPage+1>=events.data.totalPages} onClick={()=>setEventPage(page=>page+1)}>다음</button></div>}</>:<div className="filter-empty"><b>미완료 projection 이벤트가 없습니다.</b></div>}</section>
    <div className="recovery-actions"><button type="button" onClick={()=>void status.refetch()}>상태 새로고침</button><p>복구 실행은 대상 범위를 점검하고 확인한 뒤에 진행됩니다.</p></div>
  </main></div>;
}
