import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AuthContext} from '../../auth/AuthProvider';
import AdminUsersPage from './AdminUsersPage';

const apiMocks = vi.hoisted(() => ({
  fetchAdminAccounts: vi.fn(), fetchAdminAccountWarnings: vi.fn(), suspendAdminAccount: vi.fn(), activateAdminAccount: vi.fn(), warnAdminAccount: vi.fn(),
}));

vi.mock('../../api/adminAccountApi', () => apiMocks);

const activeAccount = {id: 7, email: 'collector@example.com', nickname: '피카츄 수집가', role: 'USER', status: 'ACTIVE', created_at: '2026-08-01T00:00:00Z', active_warning_count: 1, latest_active_warning_expires_at: '2026-09-01T00:00:00Z'} as const;

function renderPage() {
  return render(<QueryClientProvider client={new QueryClient({defaultOptions: {queries: {retry: false}}})}>
    <MemoryRouter><AuthContext.Provider value={{status: 'authenticated', role: 'ADMIN', retryInitialization: vi.fn()}}><AdminUsersPage/></AuthContext.Provider></MemoryRouter>
  </QueryClientProvider>);
}

describe('AdminUsersPage', () => {
  beforeEach(() => {
    apiMocks.fetchAdminAccounts.mockReset().mockResolvedValue({content: [activeAccount], page: 0, size: 20, total_elements: 1, total_pages: 1});
    apiMocks.fetchAdminAccountWarnings.mockReset().mockResolvedValue([{id: 3, order_id: 12, reason: 'BUYER_CANCELLED', issued_at: '2026-08-10T00:00:00Z', expires_at: '2026-09-10T00:00:00Z'}]);
    apiMocks.suspendAdminAccount.mockReset().mockResolvedValue(undefined);
    apiMocks.activateAdminAccount.mockReset().mockResolvedValue(undefined);
    apiMocks.warnAdminAccount.mockReset().mockResolvedValue(undefined);
  });

  it('회원 목록의 상태와 활성 경고를 표시하고 검색한다', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('피카츄 수집가')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
    expect(screen.getByText('활성 경고 1건')).toBeInTheDocument();
    await user.type(screen.getByLabelText('회원 검색'), '피카');
    await user.click(screen.getByRole('button', {name: '검색'}));
    await waitFor(() => expect(apiMocks.fetchAdminAccounts).toHaveBeenLastCalledWith({page: 0, size: 20, keyword: '피카', status: undefined, onlyWarned: false}));
  });

  it('경고 이력을 열고 확인 후 회원을 정지한다', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('피카츄 수집가');
    await user.click(screen.getByRole('button', {name: '경고 이력'}));
    expect(await screen.findByText('구매자 주문취소 경고')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: '정지'}));
    expect(screen.getByRole('dialog')).toHaveTextContent('피카츄 수집가');
    await user.click(screen.getByRole('button', {name: '정지 확인'}));
    await waitFor(() => expect(apiMocks.suspendAdminAccount).toHaveBeenCalledWith(7));
  });

  it('경고 버튼으로 관리자 경고를 발급한다', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('피카츄 수집가');
    await user.click(screen.getByRole('button', {name: '경고'}));
    expect(screen.getByRole('dialog')).toHaveTextContent('이미 활성 경고가 있어 이 경고로 자동 정지됩니다.');
    await user.click(screen.getByRole('button', {name: '경고 확인'}));
    await waitFor(() => expect(apiMocks.warnAdminAccount).toHaveBeenCalledWith(7));
  });

  it('상태 필터와 경고 필터를 적용해 목록을 다시 조회한다', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('피카츄 수집가');
    await user.selectOptions(screen.getByLabelText('상태'), 'SUSPENDED');
    await waitFor(() => expect(apiMocks.fetchAdminAccounts).toHaveBeenLastCalledWith({page: 0, size: 20, keyword: '', status: 'SUSPENDED', onlyWarned: false}));
    await user.click(screen.getByRole('button', {name: '경고 있음'}));
    await waitFor(() => expect(apiMocks.fetchAdminAccounts).toHaveBeenLastCalledWith({page: 0, size: 20, keyword: '', status: 'SUSPENDED', onlyWarned: true}));
  });
});
