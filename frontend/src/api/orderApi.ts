import type {OrderDto,OrderResponseDto} from '../dto/orderDto';
import {authenticatedRequest} from './authenticatedRequest';
import {isRedisApiProfile} from './apiProfile';

const mapOrder=(dto:OrderResponseDto):OrderDto=>{
  if(dto.id===null&&!isRedisApiProfile())throw new TypeError('주문 ID 응답이 올바르지 않습니다.');
  return {
  id:dto.id,
  auctionId:dto.auction_id,
  cardName:dto.card_name,
  price:dto.price,
  status:dto.status,
  createdAt:dto.created_at,
  streamId:dto.stream_id??null,
  projectionStatus:dto.projection_status??(dto.id===null?'PENDING':'PROJECTED'),
  };
};

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

export async function sellerCancelOrder(orderId:number):Promise<OrderDto>{
  const response=await authenticatedRequest<OrderResponseDto>(`/api/orders/${orderId}/seller-cancel`,{method:'POST'});
  return mapOrder(response);
}
