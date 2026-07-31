import {queryOptions, useQuery} from '@tanstack/react-query';
import {fetchWalletBalance} from '../api/walletApi';
import {useAuth} from '../auth/useAuth';
import {walletQueryKeys} from './walletQueryKeys';

export const walletQueries = {
  balance: () => queryOptions({
    queryKey: walletQueryKeys.balance(),
    queryFn: fetchWalletBalance,
  }),
};

export function useWalletBalance() {
  const {status} = useAuth();
  return useQuery({
    ...walletQueries.balance(),
    enabled: status === 'authenticated',
  });
}
