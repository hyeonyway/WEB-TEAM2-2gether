import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {setAccessToken} from '../../api/accessTokenStore';
import ToastContainer from '../Toast';
import WalletChargeDialog from './WalletChargeDialog';

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
      <WalletChargeDialog balance={100_000} onClose={onClose}/>
      <ToastContainer/>
    </QueryClientProvider>,
  );

  return {invalidateQueries, onClose, unmount: view.unmount};
}

describe('WalletChargeDialog', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    setAccessToken('wallet-access-token');
    vi.spyOn(globalThis.crypto, 'randomUUID')
      .mockReturnValue('11111111-1111-4111-8111-111111111111');
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

  it('409 충돌 뒤에는 새 멱등키로 재시도한다', async () => {
    vi.mocked(globalThis.crypto.randomUUID)
      .mockReturnValueOnce('11111111-1111-4111-8111-111111111111')
      .mockReturnValueOnce('33333333-3333-4333-8333-333333333333');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({}, 409))
      .mockResolvedValueOnce(jsonResponse({
        transactionId: 3,
        transactionType: 'CHARGE',
        amount: 50_000,
        balance: 150_000,
      }));
    const user = userEvent.setup();
    renderDialog();

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
      '11111111-1111-4111-8111-111111111111',
      '33333333-3333-4333-8333-333333333333',
    ]);
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
    const submitButton = screen.getByRole('button', {name: '50,000P 충전하기'});
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

    const submitButton = screen.getByRole('button', {name: '50,000P 충전하기'});
    await user.click(submitButton);

    expect(submitButton).toBeDisabled();
    expect(screen.getByRole('button', {name: '닫기'})).toBeDisabled();
    await user.click(submitButton);
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
  });
});
