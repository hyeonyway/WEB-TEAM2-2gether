import type {WalletBalanceDto} from '../dto/walletDto';
import {authenticatedRequest} from './authenticatedRequest';

function isSafeBalance(value: unknown): value is number {
  return typeof value === 'number'
    && Number.isSafeInteger(value)
    && value >= 0;
}

function isWalletBalanceDto(value: unknown): value is WalletBalanceDto {
  if (typeof value !== 'object' || value === null) return false;
  const balance = value as Partial<WalletBalanceDto>;
  return isSafeBalance(balance.totalBalance)
    && isSafeBalance(balance.frozenBalance)
    && isSafeBalance(balance.availableBalance);
}

export async function fetchWalletBalance() {
  const balance = await authenticatedRequest<unknown>('/api/wallet');
  if (!isWalletBalanceDto(balance)) {
    throw new TypeError('Wallet 잔액 응답이 안전한 정수가 아닙니다.');
  }
  return balance;
}
