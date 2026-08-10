import {queryOptions} from '@tanstack/react-query';
import {fetchPurchaseOrders,fetchSalesOrders} from '../api/orderApi';

export const orderQueryKey=['orders'] as const;

export const orderQueries={
  purchases:()=>queryOptions({
    queryKey:[...orderQueryKey,'purchases'],
    queryFn:fetchPurchaseOrders,
    // 대시보드 주문 탭을 다시 열 때마다 최신 주문 상태를 요청한다.
    staleTime:0,
  }),
  sales:()=>queryOptions({
    queryKey:[...orderQueryKey,'sales'],
    queryFn:fetchSalesOrders,
    // 대시보드 주문 탭을 다시 열 때마다 최신 주문 상태를 요청한다.
    staleTime:0,
  }),
};
