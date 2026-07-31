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
    && isSafeBalance(balance.availableBalance);
}

function isWalletTransactionDto(
  value: unknown,
  expectedType: WalletTransactionType,
): value is WalletTransactionDto {
  if (typeof value !== 'object' || value === null) return false;
  const transaction = value as Partial<WalletTransactionDto>;
  return isSafeBalance(transaction.transactionId)
    && transaction.transactionType === expectedType
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
  return transaction;
}

export function chargeWallet(variables: WalletTransactionVariables) {
  return transactWallet('/api/wallet/charges', 'CHARGE', variables);
}

export function refundWallet(variables: WalletTransactionVariables) {
  return transactWallet('/api/wallet/refunds', 'REFUND', variables);
}
