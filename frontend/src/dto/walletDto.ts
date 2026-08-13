export type WalletBalanceDto = {
  totalBalance: number;
  frozenBalance: number;
  availableBalance: number;
  walletVersion?: number;
};

export type WalletTransactionType = 'CHARGE' | 'REFUND';

export type WalletTransactionDto = {
  transactionId: number|null;
  transactionType: WalletTransactionType;
  amount: number;
  balance: number;
};

export type WalletTransactionVariables = {
  amount: number;
  idempotencyKey: string;
};
