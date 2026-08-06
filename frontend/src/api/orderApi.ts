import type {OrderDto,OrderResponseDto} from '../dto/orderDto';
import {authenticatedRequest} from './authenticatedRequest';

const mapOrder=(dto:OrderResponseDto):OrderDto=>({
  id:dto.id,
  auctionId:dto.auction_id,
  price:dto.price,
  status:dto.status,
  createdAt:dto.created_at,
});

export async function fetchPurchaseOrders():Promise<OrderDto[]>{
  const response=await authenticatedRequest<OrderResponseDto[]>('/api/orders/purchases');
  return response.map(mapOrder);
}

export async function fetchSalesOrders():Promise<OrderDto[]>{
  const response=await authenticatedRequest<OrderResponseDto[]>('/api/orders/sales');
  return response.map(mapOrder);
}

export async function confirmOrder(orderId:number):Promise<OrderDto>{
  const response=await authenticatedRequest<OrderResponseDto>(`/api/orders/${orderId}/confirm`,{method:'POST'});
  return mapOrder(response);
}

export async function cancelOrder(orderId:number):Promise<OrderDto>{
  const response=await authenticatedRequest<OrderResponseDto>(`/api/orders/${orderId}/cancel`,{method:'POST'});
  return mapOrder(response);
}
