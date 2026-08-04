import {useEffect} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {subscribeWalletChanged} from '../api/walletSyncChannel';
import {walletQueryKeys} from '../queries/walletQueryKeys';

export function useWalletCrossTabSync(enabled: boolean) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return undefined;
    return subscribeWalletChanged(() => {
      void queryClient.invalidateQueries({queryKey: walletQueryKeys.balance()});
    });
  }, [enabled, queryClient]);
}
