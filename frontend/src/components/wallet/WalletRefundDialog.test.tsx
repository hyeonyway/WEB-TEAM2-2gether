import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {setAccessToken} from '../../api/accessTokenStore';
import ToastContainer from '../Toast';
import WalletRefundDialog from './WalletRefundDialog';

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
      <WalletRefundDialog
        totalBalance={100_000}
        availableBalance={70_000}
        onClose={onClose}
      />
      <ToastContainer/>
    </QueryClientProvider>,
  );

  return {invalidateQueries, onClose, unmount: view.unmount};
}

describe('WalletRefundDialog', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    setAccessToken('wallet-access-token');
    vi.spyOn(globalThis.crypto, 'randomUUID')
      .mockReturnValue('22222222-2222-4222-8222-222222222222');
  });

  it('환불 가능액을 표시하고 가용액 초과 요청을 보내지 않는다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    renderDialog();

    expect(screen.getByText('최대 70,000P')).toBeInTheDocument();
    const amountInput = screen.getByLabelText('환불 금액');
    await user.clear(amountInput);
    await user.type(amountInput, '70001');
    await user.click(screen.getByRole('button', {name: '70,001P 환불하기'}));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('환불 금액은 가용 잔액 이하여야 합니다.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('0원 이하 환불 요청을 보내지 않는다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    renderDialog();

    const amountInput = screen.getByLabelText('환불 금액');
    await user.clear(amountInput);
    await user.type(amountInput, '0');
    await user.click(screen.getByRole('button', {name: '0P 환불하기'}));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('환불 금액은 0원보다 커야 합니다.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('성공하면 실제 차감액을 안내하고 Wallet 잔액을 다시 조회한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({
        transactionId: 3,
        transactionType: 'REFUND',
        amount: -10_000,
        balance: 90_000,
      }));
    const user = userEvent.setup();
    const {invalidateQueries, onClose} = renderDialog();

    await user.click(screen.getByRole('button', {name: '10,000P 환불하기'}));

    await waitFor(() => {
      expect(onClose).toHaveBeenCalledOnce();
    });
    expect(screen.getByText('10,000P가 환불 처리되었습니다.')).toBeInTheDocument();
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['wallet', 'balance'],
    });
    const [, options] = fetchMock.mock.calls[0] ?? [];
    expect(new Headers(options?.headers).get('Idempotency-Key'))
      .toBe('22222222-2222-4222-8222-222222222222');
  });

  it('409 뒤 입력과 멱등키를 유지해 명시적으로 재시도한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({}, 409))
      .mockResolvedValueOnce(jsonResponse({
        transactionId: 4,
        transactionType: 'REFUND',
        amount: -10_000,
        balance: 90_000,
      }));
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByRole('button', {name: '10,000P 환불하기'}));
    expect(await screen.findByRole('alert'))
      .toHaveTextContent('가용 잔액이 부족하거나 환불 요청이 충돌했습니다.');
    expect(screen.getByLabelText('환불 금액')).toHaveValue(10_000);

    await user.click(screen.getByRole('button', {name: '10,000P 환불하기'}));
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    const keys = fetchMock.mock.calls.map(([, options]) =>
      new Headers(options?.headers).get('Idempotency-Key'));
    expect(new Set(keys)).toEqual(new Set([
      '22222222-2222-4222-8222-222222222222',
    ]));
  });

  it('키보드 포커스를 가두고 Escape로 닫은 뒤 이전 포커스를 복원한다', async () => {
    const trigger = document.createElement('button');
    document.body.append(trigger);
    trigger.focus();
    const user = userEvent.setup();
    const {onClose, unmount} = renderDialog();

    await waitFor(() => {
      expect(screen.getByLabelText('환불 금액')).toHaveFocus();
    });
    const submitButton = screen.getByRole('button', {name: '10,000P 환불하기'});
    submitButton.focus();
    await user.tab();
    expect(screen.getByRole('button', {name: '닫기'})).toHaveFocus();

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledOnce();
    unmount();
    expect(trigger).toHaveFocus();
    trigger.remove();
  });
});
