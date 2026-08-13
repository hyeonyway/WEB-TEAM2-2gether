import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {fetchStreamRecoveryStatus} from '../../api/streamRecoveryApi';
import {Header} from '../../components';

export default function StreamRecoveryPage(){
  const status=useQuery({queryKey:['admin','stream-recovery','status'],queryFn:fetchStreamRecoveryStatus,refetchInterval:5000});
  if(status.isPending)return <><Header/><main><p>Stream 복구 상태를 불러오는 중입니다.</p></main></>;
  if(status.isError)return <><Header/><main><h1>접근할 수 없습니다.</h1><p>관리자 계정으로 로그인해 주세요.</p><Link to="/">홈으로 이동</Link></main></>;
  const data=status.data;
  return <><Header/><main className="stream-recovery-page">
    <small>ADMIN</small><h1>Redis Stream 복구</h1>
    <p>{data.paused?'실시간 projection이 중지되었습니다.':'실시간 projection이 정상 동작 중입니다.'}</p>
    <dl><div><dt>PENDING</dt><dd>{data.pendingCount.toLocaleString()}</dd></div><div><dt>ERROR</dt><dd>{data.errorCount.toLocaleString()}</dd></div></dl>
    <section><h2>다음 복구 대상</h2><p>{data.firstIncompleteStreamId??'미완료 이벤트가 없습니다.'}</p>{data.firstFailureMessage&&<pre>{data.firstFailureMessage}</pre>}</section>
    <button type="button" onClick={()=>void status.refetch()}>상태 새로고침</button>
    <p>복구 실행 기능은 점검 결과를 확인한 뒤 제공됩니다.</p>
  </main></>;
}
