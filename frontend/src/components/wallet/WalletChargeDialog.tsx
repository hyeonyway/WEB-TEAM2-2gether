import {useMutation, useQueryClient} from '@tanstack/react-query';
import {Wallet} from 'lucide-react';
import {type FormEvent, useState} from 'react';
import {HttpError} from '../../api/httpClient';
import {publishWalletChanged} from '../../api/walletSyncChannel';
import type {WalletBalanceDto} from '../../dto/walletDto';
import {useModalFocusTrap} from '../../hooks/useModalFocusTrap';
import {walletMutations} from '../../queries/walletMutations';
import {walletQueryKeys} from '../../queries/walletQueryKeys';
import {showToast} from '../Toast';

type WalletChargeDialogProps = {
  wallet: WalletBalanceDto;
  onClose: () => void;
};

const quickAmounts = [50_000, 100_000, 300_000];
const minimumChargeAmount = 1_000;

export default function WalletChargeDialog({
  wallet,
  onClose,
}: WalletChargeDialogProps) {
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState(0);
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const [errorMessage, setErrorMessage] = useState('');
  const isSafeAmount = Number.isSafeInteger(amount);
  const projectedTotalBalance = isSafeAmount
    && Number.isSafeInteger(wallet.totalBalance + amount)
    ? wallet.totalBalance + amount
    : null;
  const projectedAvailableBalance = isSafeAmount
    && Number.isSafeInteger(wallet.availableBalance + amount)
    ? wallet.availableBalance + amount
    : null;
  const chargeMutation = useMutation({
    ...walletMutations.charge(),
    onSuccess: transaction => {
      queryClient.setQueryData<WalletBalanceDto>(walletQueryKeys.balance(),current=>current?{
        ...current,totalBalance:transaction.balance,availableBalance:transaction.balance-current.frozenBalance,
      }:current);
      void queryClient.invalidateQueries({queryKey: walletQueryKeys.balance()});
      publishWalletChanged();
      showToast(`${transaction.amount.toLocaleString()}P가 충전되었습니다.`);
      onClose();
    },
    onError: error => {
      if (error instanceof HttpError && error.code === 'INSUFFICIENT_AVAILABLE_BALANCE') {
        void queryClient.invalidateQueries({queryKey: walletQueryKeys.balance()});
        setErrorMessage('가용 잔액이 부족합니다. 최신 잔액을 확인해 주세요.');
        return;
      }
      if (error instanceof HttpError && error.code === 'IDEMPOTENCY_CONFLICT') {
        setIdempotencyKey(crypto.randomUUID());
        setErrorMessage('충전 요청이 충돌했습니다. 새 요청으로 다시 시도해 주세요.');
        return;
      }
      setErrorMessage('충전에 실패했습니다. 같은 요청으로 다시 시도해 주세요.');
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
    if (!Number.isSafeInteger(amount)) {
      setErrorMessage('충전 금액은 안전한 정수로 입력해 주세요.');
      return;
    }
    if (amount < minimumChargeAmount) {
      setErrorMessage('충전 금액은 1,000원 이상이어야 합니다.');
      return;
    }
    if (projectedTotalBalance === null || projectedAvailableBalance === null) {
      setErrorMessage('충전 후 잔액이 안전한 범위를 초과합니다.');
      return;
    }
    setErrorMessage('');
    chargeMutation.mutate({amount, idempotencyKey});
  };

  const close = () => {
    if (!chargeMutation.isPending) onClose();
  };
  const dialogRef = useModalFocusTrap(close, chargeMutation.isPending);

  return (
    <div
      className="wallet-charge-backdrop"
      onMouseDown={event => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <section
        ref={dialogRef}
        className="wallet-charge-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="전자지갑 포인트 충전"
        tabIndex={-1}
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
        <dl className="wallet-current-balances" role="group" aria-label="현재 전자지갑 잔액">
          <div><dt>총 잔액</dt><dd>{wallet.totalBalance.toLocaleString()}P</dd></div>
          <div><dt>동결 금액</dt><dd>{wallet.frozenBalance.toLocaleString()}P</dd></div>
          <div><dt>가용 잔액</dt><dd>{wallet.availableBalance.toLocaleString()}P</dd></div>
        </dl>
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
                onClick={() => updateAmount(amount + value)}
                disabled={chargeMutation.isPending}
              >
                +{(value / 10_000).toLocaleString()}만원
              </button>
            ))}
          </div>
          <dl className="wallet-after-values">
            <div>
              <dt>충전 후 총 잔액</dt>
              <dd>{projectedTotalBalance === null
                ? '계산 불가'
                : `${projectedTotalBalance.toLocaleString()}P`}</dd>
            </div>
            <div>
              <dt>충전 후 가용 잔액</dt>
              <dd>{projectedAvailableBalance === null
                ? '계산 불가'
                : `${projectedAvailableBalance.toLocaleString()}P`}</dd>
            </div>
          </dl>
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
              : isSafeAmount
                ? `${amount.toLocaleString()}P 충전하기`
                : '충전 금액 확인'}
          </button>
        </form>
        <p>모의 충전이며 실제 결제는 진행되지 않습니다.</p>
      </section>
    </div>
  );
}
