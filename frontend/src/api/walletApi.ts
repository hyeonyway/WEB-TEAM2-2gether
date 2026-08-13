import type {
  WalletBalanceDto,
  WalletTransactionDto,
  WalletTransactionType,
  WalletTransactionVariables,
} from '../dto/walletDto';
import {authenticatedRequest} from './authenticatedRequest';

function isSafeBalance(value: unknown): value is number {
  return typeof value === 'number'
    && Number.isSafeInteger(value)
    && value >= 0;
}

function isSafeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value);
}

function isWalletBalanceDto(value: unknown): value is WalletBalanceDto {
  if (typeof value !== 'object' || value === null) return false;
  const balance = value as Partial<WalletBalanceDto>;
  return isSafeBalance(balance.totalBalance)
    && isSafeBalance(balance.frozenBalance)
    && isSafeBalance(balance.availableBalance)
    && (balance.walletVersion===undefined||isSafeInteger(balance.walletVersion));
}

function isWalletTransactionDto(
  value: unknown,
  expectedType: WalletTransactionType,
): value is WalletTransactionDto {
  if (typeof value !== 'object' || value === null) return false;
  const transaction = value as Partial<WalletTransactionDto>;
  const transactionType:unknown=transaction.transactionType;
  // The backend can route a request to the Redis approval path independently of
  // the static frontend build profile. Treat its documented event-shaped result
  // as a successful transaction instead of reporting an error after a 200.
  const redisTransaction=transaction.transactionId===null
    && transactionType===`wallet.${expectedType==='CHARGE'?'charged':'refunded'}.v1`;
  return (isSafeBalance(transaction.transactionId)||redisTransaction)
    && (transactionType === expectedType||redisTransaction)
    && isSafeInteger(transaction.amount)
    && isSafeBalance(transaction.balance);
}

export async function fetchWalletBalance() {
  const balance = await authenticatedRequest<unknown>('/api/wallet');
  if (!isWalletBalanceDto(balance)) {
    throw new TypeError('Wallet 잔액 응답이 안전한 정수가 아닙니다.');
  }
  return balance;
}

async function transactWallet(
  path: string,
  expectedType: WalletTransactionType,
  {amount, idempotencyKey}: WalletTransactionVariables,
) {
  const transaction = await authenticatedRequest<unknown>(path, {
    method: 'POST',
    headers: {'Idempotency-Key': idempotencyKey},
    body: JSON.stringify({amount}),
  });
  if (!isWalletTransactionDto(transaction, expectedType)) {
    throw new TypeError('Wallet 거래 응답이 올바르지 않습니다.');
  }
  const transactionType:unknown=transaction.transactionType;
  return {
    ...transaction,
    transactionType:transactionType==='wallet.charged.v1'?'CHARGE'
      :transactionType==='wallet.refunded.v1'?'REFUND':transactionType as WalletTransactionType,
  };
}

export function chargeWallet(variables: WalletTransactionVariables) {
  return transactWallet('/api/wallet/charges', 'CHARGE', variables);
}

export function refundWallet(variables: WalletTransactionVariables) {
  return transactWallet('/api/wallet/refunds', 'REFUND', variables);
}
