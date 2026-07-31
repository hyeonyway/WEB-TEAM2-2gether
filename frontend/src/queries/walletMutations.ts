import {chargeWallet, refundWallet} from '../api/walletApi';

export const walletMutations = {
  charge: () => ({
    mutationFn: chargeWallet,
  }),
  refund: () => ({
    mutationFn: refundWallet,
  }),
};
