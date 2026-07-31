import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {HttpError} from '../../api/httpClient';
import WalletBalance from './WalletBalance';

const {useWalletBalanceMock} = vi.hoisted(() => ({
  useWalletBalanceMock: vi.fn(),
}));

vi.mock('../../queries/walletQueries', () => ({
  useWalletBalance: useWalletBalanceMock,
}));

vi.mock('./WalletRefundDialog', () => ({
  default: ({
    totalBalance,
    availableBalance,
  }: {
    totalBalance: number;
    availableBalance: number;
  }) => (
    <div role="dialog" aria-label="전자지갑 포인트 환불">
      {totalBalance}/{availableBalance}
    </div>
  ),
}));

describe('WalletBalance', () => {
  beforeEach(() => {
    useWalletBalanceMock.mockReset();
  });

  it('조회 중에는 Wallet skeleton을 표시한다', () => {
    useWalletBalanceMock.mockReturnValue({
      data: undefined,
      error: null,
      isPending: true,
      isError: false,
      refetch: vi.fn(),
    });

    render(<WalletBalance/>);

    expect(screen.getByRole('status', {name: '전자지갑 잔액 불러오는 중'}))
      .toBeInTheDocument();
  });

  it('총잔액·동결액·가용액을 서버 필드 그대로 표시한다', () => {
    useWalletBalanceMock.mockReturnValue({
      data: {
        totalBalance: 850_000,
        frozenBalance: 120_000,
        availableBalance: 730_000,
      },
      error: null,
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });

    render(<WalletBalance/>);

    expect(screen.getByText('850,000P')).toBeInTheDocument();
    expect(screen.getByText('120,000P')).toBeInTheDocument();
    expect(screen.getByText('730,000P')).toBeInTheDocument();
    expect(screen.getByText(/활성 입찰에 동결된 금액을 제외/))
      .toBeInTheDocument();
  });

  it('환불하기를 누르면 서버의 총잔액과 가용액으로 환불 창을 연다', async () => {
    useWalletBalanceMock.mockReturnValue({
      data: {
        totalBalance: 850_000,
        frozenBalance: 120_000,
        availableBalance: 730_000,
      },
      error: null,
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    });
    const user = userEvent.setup();

    render(<WalletBalance/>);
    await user.click(screen.getByRole('button', {name: '포인트 환불하기'}));

    expect(screen.getByRole('dialog', {name: '전자지갑 포인트 환불'}))
      .toHaveTextContent('850000/730000');
  });

  it('404는 0원 대신 Wallet 준비 실패와 재시도를 표시한다', async () => {
    const refetch = vi.fn();
    useWalletBalanceMock.mockReturnValue({
      data: undefined,
      error: new HttpError(404, 'not found'),
      isPending: false,
      isError: true,
      refetch,
    });
    const user = userEvent.setup();

    render(<WalletBalance/>);

    expect(screen.getByRole('alert')).toHaveTextContent(
      '전자지갑이 아직 준비되지 않았습니다.',
    );
    expect(screen.queryByText('0P')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: '다시 시도'}));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it('네트워크 오류는 잔액 대신 재시도 가능한 오류를 표시한다', () => {
    useWalletBalanceMock.mockReturnValue({
      data: undefined,
      error: new TypeError('network error'),
      isPending: false,
      isError: true,
      refetch: vi.fn(),
    });

    render(<WalletBalance/>);

    expect(screen.getByRole('alert')).toHaveTextContent(
      '전자지갑 잔액을 불러오지 못했습니다.',
    );
    expect(screen.queryByText('0P')).not.toBeInTheDocument();
  });

  it('재조회 실패 시 이전 Wallet 잔액을 최신 값처럼 함께 표시하지 않는다', () => {
    useWalletBalanceMock.mockReturnValue({
      data: {
        totalBalance: 850_000,
        frozenBalance: 120_000,
        availableBalance: 730_000,
      },
      error: new TypeError('network error'),
      isPending: false,
      isError: true,
      refetch: vi.fn(),
    });

    render(<WalletBalance/>);

    expect(screen.getByRole('alert')).toHaveTextContent(
      '전자지갑 잔액을 불러오지 못했습니다.',
    );
    expect(screen.queryByText('850,000P')).not.toBeInTheDocument();
    expect(screen.queryByText('120,000P')).not.toBeInTheDocument();
    expect(screen.queryByText('730,000P')).not.toBeInTheDocument();
  });
});
