import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useState} from 'react';
import {Link} from 'react-router-dom';
import {cancelOrder,confirmOrder,sellerCancelOrder} from '../../../api/orderApi';
import {HttpError} from '../../../api/httpClient';
import type {OrderDto,OrderStatus} from '../../../dto/orderDto';
import type {FailedAuctionDto} from '../../../dto/auctionDto';
import {orderQueries,orderQueryKey} from '../../../queries/orderQueries';
import {auctionQueries} from '../../../queries/auctionQueries';
import {walletQueryKeys} from '../../../queries/walletQueryKeys';

type OrderRole='buyer'|'seller';
type StatusFilter='ALL'|OrderStatus|'FAILED';
type SaleRow=OrderDto|{id:number;auctionId:number;cardName:string;price:number;status:'FAILED';createdAt:string};

const baseStatusFilters:{id:StatusFilter;label:string}[]=[
  {id:'ALL',label:'전체'},
  {id:'PENDING_CONFIRM',label:'확정 대기'},
  {id:'COMPLETED',label:'거래 완료'},
  {id:'CANCELLED',label:'거래 취소'},
];

const statusLabel=(status:StatusFilter)=>
  status==='PENDING_CONFIRM'?'확정 대기':status==='COMPLETED'?'거래 완료':status==='CANCELLED'?'거래 취소':'유찰';

const toFailedRow=(auction:FailedAuctionDto):SaleRow=>({
  id:auction.id,
  auctionId:auction.id,
  cardName:auction.cardName,
  price:auction.startPrice,
  status:'FAILED',
  createdAt:auction.closedAt,
});

export default function OrdersPanel(){
  const[role,setRole]=useState<OrderRole>('buyer');
  const[statusFilter,setStatusFilter]=useState<StatusFilter>('ALL');
  const queryClient=useQueryClient();
  const orders=useQuery(role==='buyer'?orderQueries.purchases():orderQueries.sales());
  const failedAuctions=useQuery({...auctionQueries.failedForSeller(),enabled:role==='seller'});
  const changeRole=(next:OrderRole)=>{setRole(next);setStatusFilter('ALL');};
  const invalidateOrders=()=>{
    void queryClient.invalidateQueries({queryKey:orderQueryKey});
    void queryClient.invalidateQueries({queryKey:walletQueryKeys.balance()});
  };
  const confirmMutation=useMutation({mutationFn:confirmOrder,onSuccess:invalidateOrders});
  const cancelMutation=useMutation({mutationFn:cancelOrder,onSuccess:invalidateOrders});
  const sellerCancelMutation=useMutation({mutationFn:sellerCancelOrder,onSuccess:invalidateOrders});
  const actionError=confirmMutation.isError?'구매확정에 실패했습니다. 다시 시도해 주세요.'
    :cancelMutation.isError?'구매취소에 실패했습니다. 다시 시도해 주세요.'
    :sellerCancelMutation.isError?'판매취소에 실패했습니다. 다시 시도해 주세요.'
    :null;
  const statusFilters=role==='seller'?[...baseStatusFilters,{id:'FAILED',label:'유찰'} as const]:baseStatusFilters;
  const activeQuery=statusFilter==='FAILED'?failedAuctions:orders;
  const authenticationRequired=activeQuery.error instanceof HttpError && activeQuery.error.status===401;
  // 정렬은 항상 최신순 — 백엔드가 이미 id desc(생성 순서 역순)/마감 최신순으로 내려주므로 별도 정렬 UI는 두지 않는다.
  const list:SaleRow[]=statusFilter==='FAILED'
    ?(failedAuctions.data??[]).map(toFailedRow)
    :(orders.data??[]).filter(order=>statusFilter==='ALL'||order.status===statusFilter);

  return <>
    <div className="dashboard-filters" role="tablist" aria-label="주문 구분">
      <button type="button" role="tab" aria-selected={role==='buyer'} className={role==='buyer'?'active':''} onClick={()=>changeRole('buyer')}>내가 산 주문</button>
      <button type="button" role="tab" aria-selected={role==='seller'} className={role==='seller'?'active':''} onClick={()=>changeRole('seller')}>내가 판 주문</button>
    </div>
    <div className="dashboard-sort-filters" role="group" aria-label="주문 상태 필터">
      {statusFilters.map(({id,label})=>
        <button key={id} type="button" className={statusFilter===id?'active':''} onClick={()=>setStatusFilter(id)}>{label}</button>,
      )}
    </div>
    {actionError&&<p className="order-action-error" role="alert">{actionError}</p>}
    {!activeQuery.isPending&&!activeQuery.isError&&
      <p className="catalog-count">전체 {list.length.toLocaleString()}건</p>}
    <section className="cards-dash-section">
      <div className="cards-dash-section-head">
        <div><h2>{role==='buyer'?'내가 산 주문':'내가 판 주문'}</h2><p>{role==='buyer'?'낙찰받은 주문을 확정하거나 취소하세요.':'판매한 주문의 정산 상태를 확인하거나 취소하세요.'}</p></div>
      </div>
      {activeQuery.isPending
        ? <div className="filter-empty"><b>주문 목록을 불러오는 중...</b></div>
        : activeQuery.isError
          ? <div className="filter-empty">
              <b>{authenticationRequired?'로그인이 필요합니다.':'주문 목록을 불러오지 못했습니다.'}</b>
              {!authenticationRequired&&<button type="button" onClick={()=>activeQuery.refetch()}>다시 시도</button>}
            </div>
          : !list.length
            ? <div className="filter-empty"><b>조건에 맞는 주문이 없습니다.</b></div>
            : <ul className="order-list">
                {list.map(order=><li className="order-row" key={order.id??`${order.auctionId}-${'streamId' in order?order.streamId:'failed'}`}>
                  <div className="order-row-head">
                    <Link to={`/auction/${order.auctionId}`}>{order.cardName}</Link>
                    <span className={`order-status-badge ${order.status.toLowerCase()}`}>{order.status==='PENDING_CONFIRM'&&'projectionStatus' in order&&order.projectionStatus==='PENDING'?'주문 생성 중':statusLabel(order.status)}</span>
                  </div>
                  <div className="order-row-meta">
                    <span>{order.status==='FAILED'?'시작가':'거래금액'} <b>{order.price.toLocaleString()}원</b></span>
                    <span>{new Date(order.createdAt).toLocaleString()}</span>
                  </div>
                  {role==='buyer'&&order.status==='PENDING_CONFIRM'&&order.id!==null&&<div className="order-actions">
                    <button type="button" className="order-confirm-button" disabled={confirmMutation.isPending||cancelMutation.isPending} onClick={()=>confirmMutation.mutate(order.id)}>구매확정</button>
                    <button type="button" className="order-cancel-button" disabled={confirmMutation.isPending||cancelMutation.isPending} onClick={()=>cancelMutation.mutate(order.id)}>구매취소</button>
                  </div>}
                  {role==='seller'&&order.status==='PENDING_CONFIRM'&&order.id!==null&&<div className="order-actions single">
                    <button type="button" className="order-cancel-button" disabled={sellerCancelMutation.isPending} onClick={()=>sellerCancelMutation.mutate(order.id)}>판매취소</button>
                  </div>}
                </li>)}
              </ul>}
    </section>
  </>;
}
