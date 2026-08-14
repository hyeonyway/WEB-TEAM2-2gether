import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {clearCsrfToken, setCsrfToken} from '../../auth/session/csrfTokenStore';
import ToastContainer from '../Toast';
import WalletChargeDialog from './WalletChargeDialog';

class BroadcastChannelMock extends EventTarget {
  static instances: BroadcastChannelMock[] = [];
  postMessage = vi.fn();
  close = vi.fn();

  constructor(public name: string) {
    super();
    BroadcastChannelMock.instances.push(this);
  }
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function renderDialog(onClose = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: {retry: false},
      queries: {retry: false},
    },
  });
  const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');

  const view = render(
    <QueryClientProvider client={queryClient}>
      <WalletChargeDialog wallet={{
        totalBalance: 100_000,
        frozenBalance: 30_000,
        availableBalance: 70_000,
      }} onClose={onClose}/>
      <ToastContainer/>
    </QueryClientProvider>,
  );

  return {invalidateQueries, onClose, unmount: view.unmount};
}

describe('WalletChargeDialog', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    BroadcastChannelMock.instances = [];
    vi.stubGlobal('BroadcastChannel', BroadcastChannelMock);
    clearCsrfToken();
    setCsrfToken('wallet-csrf-token');
    vi.spyOn(globalThis.crypto, 'randomUUID')
      .mockReturnValue('11111111-1111-4111-8111-111111111111');
  });

  it('충전 금액은 0원에서 시작한다', () => {
    renderDialog();

    expect(screen.getByLabelText('충전 금액')).toHaveValue(0);
    expect(screen.getByRole('button', {name: '0P 충전하기'}))
      .toBeInTheDocument();
  });

  it('현재 Wallet의 총 잔액과 동결 금액과 가용 잔액을 함께 표시한다', () => {
    const queryClient = new QueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <WalletChargeDialog
          wallet={{
            totalBalance: 100_000,
            frozenBalance: 30_000,
            availableBalance: 70_000,
          }}
          onClose={vi.fn()}
        />
      </QueryClientProvider>,
    );

    const currentBalances=within(screen.getByRole('group',{name:'현재 전자지갑 잔액'}));
    expect(currentBalances.getByText('총 잔액')).toBeInTheDocument();
    expect(currentBalances.getByText('100,000P')).toBeInTheDocument();
    expect(currentBalances.getByText('동결 금액')).toBeInTheDocument();
    expect(currentBalances.getByText('30,000P')).toBeInTheDocument();
    expect(currentBalances.getByText('가용 잔액')).toBeInTheDocument();
    expect(currentBalances.getByText('70,000P')).toBeInTheDocument();
  });

  it('1,000원 미만 금액은 충전 요청을 보내지 않는다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    renderDialog();

    const amountInput = screen.getByLabelText('충전 금액');
    await user.clear(amountInput);
    await user.type(amountInput, '999');
    await user.click(screen.getByRole('button', {name: '999P 충전하기'}));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('충전 금액은 1,000원 이상이어야 합니다.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('금액 버튼은 현재 입력한 충전 금액에 누적한다', async () => {
    const user = userEvent.setup();
    renderDialog();

    const amountInput = screen.getByLabelText('충전 금액');
    await user.clear(amountInput);
    await user.type(amountInput, '200000');
    await user.click(screen.getByRole('button', {name: '+5만원'}));

    expect(amountInput).toHaveValue(250_000);
    expect(screen.getByRole('button', {name: '250,000P 충전하기'}))
      .toBeInTheDocument();
  });

  it('성공하면 거래 금액을 안내하고 Wallet 잔액을 다시 조회한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({
        transactionId: 1,
        transactionType: 'CHARGE',
        amount: 50_000,
        balance: 150_000,
      }));
    const user = userEvent.setup();
    const {invalidateQueries, onClose} = renderDialog();

    await user.click(screen.getByRole('button', {name: '+5만원'}));
    await user.click(screen.getByRole('button', {name: '50,000P 충전하기'}));

    await waitFor(() => {
      expect(onClose).toHaveBeenCalledOnce();
    });
    expect(screen.getByText('50,000P가 충전되었습니다.')).toBeInTheDocument();
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['wallet', 'balance'],
    });
    const [, options] = fetchMock.mock.calls[0] ?? [];
    expect(new Headers(options?.headers).get('Idempotency-Key'))
      .toBe('11111111-1111-4111-8111-111111111111');
    expect(BroadcastChannelMock.instances).toHaveLength(1);
    expect(BroadcastChannelMock.instances[0]?.postMessage).toHaveBeenCalledWith({
      type: 'WALLET_CHANGED',
    });
  });

  it('네트워크 실패를 재시도할 때 같은 멱등키를 유지한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new TypeError('network failed'))
      .mockResolvedValueOnce(jsonResponse({
        transactionId: 2,
        transactionType: 'CHARGE',
        amount: 50_000,
        balance: 150_000,
      }));
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByRole('button', {name: '+5만원'}));
    await user.click(screen.getByRole('button', {name: '50,000P 충전하기'}));
    expect(await screen.findByRole('alert'))
      .toHaveTextContent('충전에 실패했습니다. 같은 요청으로 다시 시도해 주세요.');

    await user.click(screen.getByRole('button', {name: '50,000P 충전하기'}));
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    const keys = fetchMock.mock.calls.map(([, options]) =>
      new Headers(options?.headers).get('Idempotency-Key'));
    expect(keys).toEqual([
      '11111111-1111-4111-8111-111111111111',
      '11111111-1111-4111-8111-111111111111',
    ]);
  });

  it('멱등키 충돌 뒤에는 새 멱등키로 재시도한다', async () => {
    vi.mocked(globalThis.crypto.randomUUID)
      .mockReturnValueOnce('11111111-1111-4111-8111-111111111111')
      .mockReturnValueOnce('22222222-2222-4222-8222-222222222222')
      .mockReturnValueOnce('33333333-3333-4333-8333-333333333333');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        code: 'IDEMPOTENCY_CONFLICT',
        message: '같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다.',
      }, 409))
      .mockResolvedValueOnce(jsonResponse({
        transactionId: 3,
        transactionType: 'CHARGE',
        amount: 50_000,
        balance: 150_000,
      }));
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByRole('button', {name: '+5만원'}));
    await user.click(screen.getByRole('button', {name: '50,000P 충전하기'}));
    expect(await screen.findByRole('alert'))
      .toHaveTextContent('충전 요청이 충돌했습니다.');

    await user.click(screen.getByRole('button', {name: '50,000P 충전하기'}));
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    const keys = fetchMock.mock.calls.map(([, options]) =>
      new Headers(options?.headers).get('Idempotency-Key'));
    expect(keys).toEqual([
      '22222222-2222-4222-8222-222222222222',
      '33333333-3333-4333-8333-333333333333',
    ]);
  });

  it('가용 잔액 충돌은 Wallet을 다시 조회하고 같은 멱등키로 재시도한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        code: 'INSUFFICIENT_AVAILABLE_BALANCE',
        message: '사용 가능한 잔액이 부족합니다.',
      }, 409))
      .mockResolvedValueOnce(jsonResponse({
        transactionId: 4,
        transactionType: 'CHARGE',
        amount: 50_000,
        balance: 150_000,
      }));
    const user = userEvent.setup();
    const {invalidateQueries} = renderDialog();

    await user.click(screen.getByRole('button', {name: '+5만원'}));
    await user.click(screen.getByRole('button', {name: '50,000P 충전하기'}));
    expect(await screen.findByRole('alert'))
      .toHaveTextContent('가용 잔액이 부족합니다. 최신 잔액을 확인해 주세요.');
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['wallet', 'balance'],
    });

    await user.click(screen.getByRole('button', {name: '50,000P 충전하기'}));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));

    const keys = fetchMock.mock.calls.map(([, options]) =>
      new Headers(options?.headers).get('Idempotency-Key'));
    expect(new Set(keys)).toEqual(new Set([
      '11111111-1111-4111-8111-111111111111',
    ]));
  });

  it('충전 후 잔액이 안전한 정수 범위를 넘으면 요청과 예상 합계를 막는다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    const queryClient = new QueryClient({
      defaultOptions: {mutations: {retry: false}},
    });
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={queryClient}>
        <WalletChargeDialog
          wallet={{
            totalBalance: Number.MAX_SAFE_INTEGER - 10_000,
            frozenBalance: 0,
            availableBalance: Number.MAX_SAFE_INTEGER - 10_000,
          }}
          onClose={vi.fn()}
        />
      </QueryClientProvider>,
    );

    await user.click(screen.getByRole('button', {name: '+5만원'}));

    expect(screen.getAllByText('계산 불가')).toHaveLength(2);
    await user.click(screen.getByRole('button', {name: '50,000P 충전하기'}));
    expect(await screen.findByRole('alert'))
      .toHaveTextContent('충전 후 잔액이 안전한 범위를 초과합니다.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('유한하지 않은 입력값은 금액으로 표시하거나 요청하지 않는다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    renderDialog();

    fireEvent.change(screen.getByLabelText('충전 금액'), {
      target: {value: '1e308'},
    });

    expect(screen.queryByText('∞P')).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: '충전 금액 확인'}))
      .toBeInTheDocument();
    expect(screen.getAllByText('계산 불가')).toHaveLength(2);
    await userEvent.setup().click(screen.getByRole('button', {name: '충전 금액 확인'}));
    expect(await screen.findByRole('alert'))
      .toHaveTextContent('충전 금액은 안전한 정수로 입력해 주세요.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('키보드 포커스를 가두고 Escape로 닫은 뒤 이전 포커스를 복원한다', async () => {
    const trigger = document.createElement('button');
    document.body.append(trigger);
    trigger.focus();
    const user = userEvent.setup();
    const {onClose, unmount} = renderDialog();

    await waitFor(() => {
      expect(screen.getByLabelText('충전 금액')).toHaveFocus();
    });
    const submitButton = screen.getByRole('button', {name: '0P 충전하기'});
    submitButton.focus();
    await user.tab();
    expect(screen.getByRole('button', {name: '닫기'})).toHaveFocus();

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledOnce();
    unmount();
    expect(trigger).toHaveFocus();
    trigger.remove();
  });

  it('제출 중에는 닫기와 중복 제출을 막는다', async () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    const {onClose} = renderDialog();

    await user.click(screen.getByRole('button', {name: '+5만원'}));
    const submitButton = screen.getByRole('button', {name: '50,000P 충전하기'});
    await user.click(submitButton);

    expect(submitButton).toBeDisabled();
    expect(screen.getByRole('button', {name: '닫기'})).toBeDisabled();
    await user.click(submitButton);
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
  });
});
