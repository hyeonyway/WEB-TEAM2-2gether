export const walletQueryKeys = {
  all: ['wallet'] as const,
  balance: () => [...walletQueryKeys.all, 'balance'] as const,
};
