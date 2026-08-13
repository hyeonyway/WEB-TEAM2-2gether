import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {fetchStreamRecoveryStatus} from '../../api/streamRecoveryApi';
import {Header} from '../../components';
import {useAuth} from '../../auth/useAuth';
import {HttpError} from '../../api/httpClient';
import './StreamRecoveryPage.css';

export default function StreamRecoveryPage(){
  const {status:authStatus}=useAuth();
  const status=useQuery({queryKey:['admin','stream-recovery','status'],queryFn:fetchStreamRecoveryStatus,enabled:authStatus==='authenticated',refetchInterval:5000});
  if(authStatus==='initializing')return <><Header/><main className="stream-recovery-page"><p>인증 상태를 확인하고 있습니다.</p></main></>;
  if(authStatus==='anonymous')return <><Header/><main className="stream-recovery-page recovery-empty"><small>ADMIN CONSOLE</small><h1>Stream 복구 관리자</h1><p>장애 상태와 복구 대상을 확인하려면 관리자 계정으로 로그인해 주세요.</p><div className="recovery-login-guide"><b>로그인</b><span>오른쪽 상단의 로그인 버튼을 눌러 관리자 계정으로 로그인할 수 있습니다.</span></div><Link className="recovery-home-link" to="/">홈으로 이동</Link></main></>;
  if(status.isPending)return <><Header/><main className="stream-recovery-page"><p>Stream 복구 상태를 불러오는 중입니다.</p></main></>;
  const noAdminAccess=status.error instanceof HttpError&&status.error.status===403;
  if(status.isError)return <><Header/><main className="stream-recovery-page recovery-empty"><small>ADMIN CONSOLE</small><h1>{noAdminAccess?'관리자 권한이 필요합니다.':'복구 상태를 불러오지 못했습니다.'}</h1><p>{noAdminAccess?'현재 로그인한 계정에는 Stream 복구 권한이 없습니다.':'잠시 후 다시 시도해 주세요.'}</p><button type="button" onClick={()=>void status.refetch()}>다시 시도</button></main></>;
  const data=status.data;
  return <><Header/><main className="stream-recovery-page">
    <div className="recovery-hero"><div><small>ADMIN CONSOLE</small><h1>Redis Stream 복구</h1><p>MySQL projection 지연과 오류를 확인하고 안전한 복구를 준비합니다.</p></div><span className={`recovery-state ${data.paused?'paused':'healthy'}`}>{data.paused?'CONSUMER PAUSED':'CONSUMER RUNNING'}</span></div>
    <section className="recovery-summary" aria-label="projection 상태 요약"><article><small>PENDING</small><strong>{data.pendingCount.toLocaleString()}</strong><span>수신 후 반영 대기 이벤트</span></article><article className={data.errorCount?'danger':''}><small>ERROR</small><strong>{data.errorCount.toLocaleString()}</strong><span>원인 확인이 필요한 이벤트</span></article></section>
    <section className="recovery-target"><div><small>RECOVERY START POINT</small><h2>다음 복구 대상</h2></div><code>{data.firstIncompleteStreamId??'미완료 이벤트가 없습니다.'}</code>{data.firstFailureMessage&&<p className="recovery-error-message">{data.firstFailureMessage}</p>}</section>
    <div className="recovery-actions"><button type="button" onClick={()=>void status.refetch()}>상태 새로고침</button><p>복구 실행은 대상 범위를 점검하고 확인한 뒤에 진행됩니다.</p></div>
  </main></>;
}
