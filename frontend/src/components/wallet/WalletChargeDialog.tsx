import {useMutation, useQueryClient} from '@tanstack/react-query';
import {Wallet} from 'lucide-react';
import {type FormEvent, useState} from 'react';
import {HttpError} from '../../api/httpClient';
import {walletMutations} from '../../queries/walletMutations';
import {walletQueryKeys} from '../../queries/walletQueryKeys';
import {showToast} from '../Toast';

type WalletChargeDialogProps = {
  balance: number;
  onClose: () => void;
};

const quickAmounts = [50_000, 100_000, 300_000];
const minimumChargeAmount = 1_000;

export default function WalletChargeDialog({
  balance,
  onClose,
}: WalletChargeDialogProps) {
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState(quickAmounts[0]);
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const [errorMessage, setErrorMessage] = useState('');
  const chargeMutation = useMutation({
    ...walletMutations.charge(),
    onSuccess: transaction => {
      void queryClient.invalidateQueries({queryKey: walletQueryKeys.balance()});
      showToast(`${transaction.amount.toLocaleString()}P가 충전되었습니다.`);
      onClose();
    },
    onError: error => {
      setErrorMessage(
        error instanceof HttpError && error.status === 409
          ? '충전 요청이 충돌했습니다. 창을 닫고 새 거래로 다시 시도해 주세요.'
          : '충전에 실패했습니다. 같은 요청으로 다시 시도해 주세요.',
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
    if (chargeMutation.isPending) return;
    if (!Number.isSafeInteger(amount) || amount < minimumChargeAmount) {
      setErrorMessage('충전 금액은 1,000원 이상이어야 합니다.');
      return;
    }
    setErrorMessage('');
    chargeMutation.mutate({amount, idempotencyKey});
  };

  const close = () => {
    if (!chargeMutation.isPending) onClose();
  };

  return (
    <div
      className="wallet-charge-backdrop"
      onMouseDown={event => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <section
        className="wallet-charge-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="전자지갑 포인트 충전"
      >
        <button
          type="button"
          className="wallet-charge-close"
          onClick={close}
          aria-label="닫기"
          disabled={chargeMutation.isPending}
        >
          ×
        </button>
        <div className="wallet-charge-title">
          <span><Wallet/></span>
          <div>
            <small>전자지갑</small>
            <h2>포인트 충전</h2>
          </div>
        </div>
        <div className="wallet-current">
          <span>현재 보유 포인트</span>
          <strong>{balance.toLocaleString()}P</strong>
        </div>
        <form noValidate onSubmit={submit}>
          <label className="wallet-amount-label">
            충전 금액
            <input
              type="number"
              min={minimumChargeAmount}
              step={1_000}
              value={amount}
              onChange={event => updateAmount(Number(event.target.value))}
              disabled={chargeMutation.isPending}
            />
          </label>
          <div className="wallet-charge-options">
            {quickAmounts.map(value => (
              <button
                key={value}
                type="button"
                className={amount === value ? 'active' : ''}
                onClick={() => updateAmount(value)}
                disabled={chargeMutation.isPending}
              >
                +{(value / 10_000).toLocaleString()}만원
              </button>
            ))}
          </div>
          <div className="wallet-after">
            <span>충전 후 포인트</span>
            <b>{(balance + Math.max(amount, 0)).toLocaleString()}P</b>
          </div>
          {errorMessage && (
            <p className="wallet-transaction-error" role="alert">{errorMessage}</p>
          )}
          <button
            className="wallet-charge-submit"
            type="submit"
            disabled={chargeMutation.isPending}
          >
            {chargeMutation.isPending
              ? '충전 중...'
              : `${amount.toLocaleString()}P 충전하기`}
          </button>
        </form>
        <p>모의 충전이며 실제 결제는 진행되지 않습니다.</p>
      </section>
    </div>
  );
}
