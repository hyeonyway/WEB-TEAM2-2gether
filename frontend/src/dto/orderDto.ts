export type OrderStatus='PENDING_CONFIRM'|'COMPLETED'|'CANCELLED';

export type OrderResponseDto={
  id:number;
  auction_id:number;
  price:number;
  status:OrderStatus;
  created_at:string;
};

export type OrderDto={
  id:number;
  auctionId:number;
  price:number;
  status:OrderStatus;
  createdAt:string;
};
