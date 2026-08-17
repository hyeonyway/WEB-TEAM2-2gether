import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useState} from 'react';
import {Link} from 'react-router-dom';
import {activateAdminAccount, fetchAdminAccountWarnings, fetchAdminAccounts, suspendAdminAccount, warnAdminAccount, type AdminAccountDto, type AdminAccountStatus} from '../../api/adminAccountApi';
import {HttpError} from '../../api/httpClient';
import {AdminNav, Header} from '../../components';
import {useAuth} from '../../auth/useAuth';
import './AdminUsersPage.css';

const pageSize = 20;

const warningReasonLabels: Record<string, string> = {
  BUYER_CANCELLED: '구매자 주문취소 경고',
  SELLER_CANCELLED: '판매자 주문취소 경고',
  ADMIN_MANUAL: '관리자 경고',
};

function toKoreanDate(value: string | null) {
  return value ? new Date(value).toLocaleString('ko-KR') : '-';
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof HttpError ? error.message : fallback;
}

function warningReasonLabel(reason: string) {
  return warningReasonLabels[reason] ?? reason;
}

export default function AdminUsersPage() {
  const {status: authStatus} = useAuth();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<AdminAccountStatus | ''>('');
  const [onlyWarned, setOnlyWarned] = useState(false);
  const [warningsFor, setWarningsFor] = useState<AdminAccountDto | null>(null);
  const [actionTarget, setActionTarget] = useState<AdminAccountDto | null>(null);
  const [warnTarget, setWarnTarget] = useState<AdminAccountDto | null>(null);
  const accounts = useQuery({
    queryKey: ['admin', 'users', page, keyword, statusFilter, onlyWarned],
    queryFn: () => fetchAdminAccounts({page, size: pageSize, keyword, status: statusFilter || undefined, onlyWarned}),
    enabled: authStatus === 'authenticated',
  });
  const warnings = useQuery({queryKey: ['admin', 'users', warningsFor?.id, 'warnings'], queryFn: () => fetchAdminAccountWarnings(warningsFor!.id), enabled: warningsFor !== null});
  const updateStatus = useMutation({
    mutationFn: (target: AdminAccountDto) => target.status === 'ACTIVE' ? suspendAdminAccount(target.id) : activateAdminAccount(target.id),
    onSuccess: () => {
      setActionTarget(null);
      void queryClient.invalidateQueries({queryKey: ['admin', 'users']});
    },
  });
  const issueWarning = useMutation({
    mutationFn: (target: AdminAccountDto) => warnAdminAccount(target.id),
    onSuccess: () => {
      setWarnTarget(null);
      void queryClient.invalidateQueries({queryKey: ['admin', 'users']});
    },
  });

  const search = () => {
    setPage(0);
    setKeyword(keywordInput.trim());
  };
  const applyStatusFilter = (value: AdminAccountStatus | '') => {
    setPage(0);
    setStatusFilter(value);
  };
  const toggleOnlyWarned = () => {
    setPage(0);
    setOnlyWarned(value => !value);
  };
  if (authStatus === 'initializing') return <div className="cards-mypage standalone-dashboard"><Header/><main className="admin-users-page"><p>인증 상태를 확인하고 있습니다.</p></main></div>;
  if (authStatus === 'anonymous') return <div className="cards-mypage standalone-dashboard"><Header/><main className="admin-users-page admin-users-empty"><small>ADMIN CONSOLE</small><h1>회원 관리</h1><p>회원 정보를 관리하려면 관리자 계정으로 로그인해 주세요.</p><Link to="/">홈으로 이동</Link></main></div>;
  if (accounts.isPending) return <div className="cards-mypage standalone-dashboard"><Header/><main className="admin-users-page"><p>회원 목록을 불러오는 중입니다.</p></main></div>;
  if (accounts.isError) return <div className="cards-mypage standalone-dashboard"><Header/><main className="admin-users-page admin-users-empty"><small>ADMIN CONSOLE</small><h1>{accounts.error instanceof HttpError && accounts.error.status === 403 ? '관리자 권한이 필요합니다.' : '회원 목록을 불러오지 못했습니다.'}</h1><p>{errorMessage(accounts.error, '잠시 후 다시 시도해 주세요.')}</p><button type="button" onClick={() => void accounts.refetch()}>다시 시도</button></main></div>;
  const data = accounts.data;
  return <div className="cards-mypage standalone-dashboard"><Header/><main className="admin-users-page">
    <div className="admin-users-title"><div><small>ADMIN CONSOLE</small><h1>회원 관리</h1><p>회원 상태와 주문 취소 경고 이력을 확인하고 이용 제한을 관리합니다.</p></div><AdminNav/></div>
    <form className="admin-users-search" onSubmit={event => {event.preventDefault(); search();}}>
      <label htmlFor="admin-user-search">회원 검색</label>
      <input id="admin-user-search" value={keywordInput} onChange={event => setKeywordInput(event.target.value)} placeholder="이메일 또는 닉네임"/>
      <button type="submit">검색</button>
      <label htmlFor="admin-user-status-filter">상태</label>
      <select id="admin-user-status-filter" value={statusFilter} onChange={event => applyStatusFilter(event.target.value as AdminAccountStatus | '')}>
        <option value="">전체</option>
        <option value="ACTIVE">활성</option>
        <option value="SUSPENDED">정지됨</option>
      </select>
      <button type="button" className={onlyWarned ? 'admin-filter-toggle active' : 'admin-filter-toggle'} onClick={toggleOnlyWarned}>경고 있음</button>
    </form>
    <section className="admin-users-list" aria-label="회원 목록"><div className="admin-users-list-head"><div><h2>회원 목록</h2><p>이메일과 닉네임으로 회원을 검색할 수 있습니다.</p></div><span>전체 {data.total_elements.toLocaleString()}명</span></div>
      {data.content.length ? <><div className="admin-users-table" role="table"><div className="admin-users-row admin-users-head" role="row"><span>회원</span><span>상태</span><span>활성 경고</span><span>가입일</span><span>관리</span></div>{data.content.map(account => <div className="admin-users-row" role="row" key={account.id}><div><b>{account.nickname}</b><small>{account.email} · {account.role}</small></div><span><b className={`admin-status ${account.status.toLowerCase()}`}>{account.status}</b></span><div>{account.active_warning_count > 0 ? <><b className="admin-warning">활성 경고 {account.active_warning_count}건</b><small>만료 {toKoreanDate(account.latest_active_warning_expires_at)}</small></> : <span>-</span>}</div><time>{toKoreanDate(account.created_at)}</time><div className="admin-user-actions"><button type="button" onClick={() => setWarningsFor(account)}>경고 이력</button>{account.status !== 'WITHDRAWN' && <button type="button" onClick={() => {setWarningsFor(null); setWarnTarget(account);}}>경고</button>}{account.status !== 'WITHDRAWN' && <button type="button" className={account.status === 'ACTIVE' ? 'danger' : ''} onClick={() => {setWarningsFor(null); setActionTarget(account);}}>{account.status === 'ACTIVE' ? '정지' : '활성화'}</button>}</div></div>)}</div>{data.total_pages > 1 && <div className="admin-users-pagination"><button type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)}>이전</button><span>{page + 1} / {data.total_pages}</span><button type="button" disabled={page + 1 >= data.total_pages} onClick={() => setPage(value => value + 1)}>다음</button></div>}</> : <div className="admin-users-no-results">검색 조건에 맞는 회원이 없습니다.</div>}
    </section>
  </main>
  {warningsFor && <div className="admin-modal-backdrop" onMouseDown={event => event.target === event.currentTarget && setWarningsFor(null)}><section className="admin-modal" role="dialog" aria-modal="true" aria-label={`${warningsFor.nickname} 경고 이력`}><h2>{warningsFor.nickname} 경고 이력</h2>{warnings.isPending ? <p>경고 이력을 불러오는 중입니다.</p> : warnings.isError ? <><p className="admin-modal-error">{errorMessage(warnings.error, '경고 이력을 불러오지 못했습니다.')}</p><button type="button" onClick={() => void warnings.refetch()}>다시 시도</button></> : warnings.data?.length ? <ul>{warnings.data.map(warning => <li key={warning.id}><b>{warningReasonLabel(warning.reason)}</b><span>{warning.order_id !== null && <>주문 #{warning.order_id} · </>}{toKoreanDate(warning.issued_at)} 발행 · {toKoreanDate(warning.expires_at)} 만료</span></li>)}</ul> : <p>등록된 경고 이력이 없습니다.</p>}<div className="admin-modal-actions"><button type="button" onClick={() => setWarningsFor(null)}>닫기</button></div></section></div>}
  {actionTarget && <div className="admin-modal-backdrop" onMouseDown={event => event.target === event.currentTarget && !updateStatus.isPending && setActionTarget(null)}><section className="admin-modal" role="dialog" aria-modal="true" aria-label="회원 상태 변경 확인"><small>MEMBERSHIP STATUS</small><h2>{actionTarget.status === 'ACTIVE' ? '회원 이용을 정지할까요?' : '회원 이용을 활성화할까요?'}</h2><p><b>{actionTarget.nickname}</b> ({actionTarget.email}) 계정의 상태를 변경합니다.</p>{updateStatus.isError && <p className="admin-modal-error">{errorMessage(updateStatus.error, '상태 변경에 실패했습니다.')}</p>}<div className="admin-modal-actions"><button type="button" disabled={updateStatus.isPending} onClick={() => setActionTarget(null)}>취소</button><button type="button" disabled={updateStatus.isPending} onClick={() => updateStatus.mutate(actionTarget)}>{actionTarget.status === 'ACTIVE' ? '정지 확인' : '활성화 확인'}</button></div></section></div>}
  {warnTarget && <div className="admin-modal-backdrop" onMouseDown={event => event.target === event.currentTarget && !issueWarning.isPending && setWarnTarget(null)}><section className="admin-modal" role="dialog" aria-modal="true" aria-label="회원 경고 확인"><small>MEMBERSHIP WARNING</small><h2>{warnTarget.nickname} 님에게 경고를 줄까요?</h2><p><b>{warnTarget.nickname}</b> ({warnTarget.email}) 계정에 관리자 경고를 발급합니다.</p>{warnTarget.active_warning_count >= 1 && <p className="admin-modal-warning">이미 활성 경고가 있어 이 경고로 자동 정지됩니다.</p>}{issueWarning.isError && <p className="admin-modal-error">{errorMessage(issueWarning.error, '경고 발급에 실패했습니다.')}</p>}<div className="admin-modal-actions"><button type="button" disabled={issueWarning.isPending} onClick={() => setWarnTarget(null)}>취소</button><button type="button" disabled={issueWarning.isPending} onClick={() => issueWarning.mutate(warnTarget)}>경고 확인</button></div></section></div>}
  </div>;
}
