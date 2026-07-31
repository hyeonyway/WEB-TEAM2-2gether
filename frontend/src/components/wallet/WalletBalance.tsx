import {Wallet} from 'lucide-react';
import {useState} from 'react';
import {HttpError} from '../../api/httpClient';
import {useWalletBalance} from '../../queries/walletQueries';
import WalletRefundDialog from './WalletRefundDialog';

function formatPoints(amount: number) {
  return `${amount.toLocaleString()}P`;
}

export default function WalletBalance() {
  const walletQuery = useWalletBalance();
  const [refundOpen, setRefundOpen] = useState(false);

  return (
    <>
      <section className="subview-card wallet-balance-card" aria-label="전자지갑 잔액">
        <div className="wallet-balance-title">
          <Wallet/>
          <span>
            <small>MY WALLET</small>
            <h2>전자지갑</h2>
          </span>
        </div>
        {walletQuery.isPending && (
          <div
            className="wallet-balance-loading"
            role="status"
            aria-label="전자지갑 잔액 불러오는 중"
          />
        )}
        {walletQuery.isError && (
          <div className="wallet-balance-error" role="alert">
            <p>
              {walletQuery.error instanceof HttpError && walletQuery.error.status === 404
                ? '전자지갑이 아직 준비되지 않았습니다.'
                : '전자지갑 잔액을 불러오지 못했습니다.'}
            </p>
            <button type="button" onClick={() => void walletQuery.refetch()}>
              다시 시도
            </button>
          </div>
        )}
        {!walletQuery.isError && walletQuery.data && (
          <>
            <dl className="wallet-balance-values">
              <div>
                <dt>총 잔액</dt>
                <dd>{formatPoints(walletQuery.data.totalBalance)}</dd>
              </div>
              <div>
                <dt>동결 금액</dt>
                <dd>{formatPoints(walletQuery.data.frozenBalance)}</dd>
              </div>
              <div>
                <dt>가용 잔액</dt>
                <dd>{formatPoints(walletQuery.data.availableBalance)}</dd>
              </div>
            </dl>
            <p className="wallet-balance-help">
              가용 잔액은 활성 입찰에 동결된 금액을 제외한 금액입니다.
            </p>
            <button
              type="button"
              className="wallet-refund-open"
              onClick={() => setRefundOpen(true)}
              disabled={walletQuery.data.availableBalance <= 0}
            >
              포인트 환불하기
            </button>
          </>
        )}
      </section>
      {refundOpen && walletQuery.data && (
        <WalletRefundDialog
          totalBalance={walletQuery.data.totalBalance}
          availableBalance={walletQuery.data.availableBalance}
          onClose={() => setRefundOpen(false)}
        />
      )}
    </>
  );
}
