import {queryOptions} from '@tanstack/react-query';
import {fetchPurchaseOrders,fetchSalesOrders} from '../api/orderApi';

export const orderQueryKey=['orders'] as const;

export const orderQueries={
  purchases:()=>queryOptions({
    queryKey:[...orderQueryKey,'purchases'],
    queryFn:fetchPurchaseOrders,
    staleTime:10_000,
  }),
  sales:()=>queryOptions({
    queryKey:[...orderQueryKey,'sales'],
    queryFn:fetchSalesOrders,
    staleTime:10_000,
  }),
};
