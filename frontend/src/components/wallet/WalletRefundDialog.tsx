import {useMutation, useQueryClient} from '@tanstack/react-query';
import {Wallet} from 'lucide-react';
import {type FormEvent, useState} from 'react';
import {HttpError} from '../../api/httpClient';
import {walletMutations} from '../../queries/walletMutations';
import {walletQueryKeys} from '../../queries/walletQueryKeys';
import {showToast} from '../Toast';

type WalletRefundDialogProps = {
  totalBalance: number;
  availableBalance: number;
  onClose: () => void;
};

export default function WalletRefundDialog({
  totalBalance,
  availableBalance,
  onClose,
}: WalletRefundDialogProps) {
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState(Math.min(10_000, availableBalance));
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const [errorMessage, setErrorMessage] = useState('');
  const refundMutation = useMutation({
    ...walletMutations.refund(),
    onSuccess: transaction => {
      void queryClient.invalidateQueries({queryKey: walletQueryKeys.balance()});
      showToast(`${Math.abs(transaction.amount).toLocaleString()}P가 환불 처리되었습니다.`);
      onClose();
    },
    onError: error => {
      setErrorMessage(
        error instanceof HttpError && error.status === 409
          ? '가용 잔액이 부족하거나 환불 요청이 충돌했습니다.'
          : '환불에 실패했습니다. 같은 요청으로 다시 시도해 주세요.',
      );
    },
  });

  const updateAmount = (nextAmount: number) => {
    setAmount(nextAmount);
    setIdempotencyKey(crypto.randomUUID());
    setErrorMessage('');
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (refundMutation.isPending) return;
    if (!Number.isSafeInteger(amount) || amount <= 0) {
      setErrorMessage('환불 금액은 0원보다 커야 합니다.');
      return;
    }
    if (amount > availableBalance) {
      setErrorMessage('환불 금액은 가용 잔액 이하여야 합니다.');
      return;
    }
    setErrorMessage('');
    refundMutation.mutate({amount, idempotencyKey});
  };

  const close = () => {
    if (!refundMutation.isPending) onClose();
  };

  return (
    <div
      className="wallet-charge-backdrop"
      onMouseDown={event => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <section
        className="wallet-charge-dialog wallet-refund-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="전자지갑 포인트 환불"
      >
        <button
          type="button"
          className="wallet-charge-close"
          onClick={close}
          aria-label="닫기"
          disabled={refundMutation.isPending}
        >
          ×
        </button>
        <div className="wallet-charge-title">
          <span><Wallet/></span>
          <div>
            <small>전자지갑</small>
            <h2>포인트 환불</h2>
          </div>
        </div>
        <div className="wallet-refund-balances">
          <span>
            <small>총 잔액</small>
            <strong>{totalBalance.toLocaleString()}P</strong>
          </span>
          <span>
            <small>환불 가능</small>
            <strong>최대 {availableBalance.toLocaleString()}P</strong>
          </span>
        </div>
        <form noValidate onSubmit={submit}>
          <label className="wallet-amount-label">
            환불 금액
            <input
              type="number"
              min={1}
              max={availableBalance}
              step={1_000}
              value={amount}
              onChange={event => updateAmount(Number(event.target.value))}
              disabled={refundMutation.isPending}
            />
          </label>
          <button
            className="wallet-refund-maximum"
            type="button"
            onClick={() => updateAmount(availableBalance)}
            disabled={refundMutation.isPending || availableBalance <= 0}
          >
            환불 가능액 전부 입력
          </button>
          <div className="wallet-after">
            <span>환불 후 총 포인트</span>
            <b>{Math.max(totalBalance - Math.max(amount, 0), 0).toLocaleString()}P</b>
          </div>
          {errorMessage && (
            <p className="wallet-transaction-error" role="alert">{errorMessage}</p>
          )}
          <button
            className="wallet-charge-submit"
            type="submit"
            disabled={refundMutation.isPending}
          >
            {refundMutation.isPending
              ? '환불 처리 중...'
              : `${amount.toLocaleString()}P 환불하기`}
          </button>
        </form>
        <p>모의 환불이며 실제 계좌 입금은 진행되지 않습니다.</p>
      </section>
    </div>
  );
}
