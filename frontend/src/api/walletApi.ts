import type {WalletBalanceDto} from '../dto/walletDto';
import {authenticatedRequest} from './authenticatedRequest';

export function fetchWalletBalance() {
  return authenticatedRequest<WalletBalanceDto>('/api/wallet');
}
