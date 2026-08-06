export type OrderStatus='PENDING_CONFIRM'|'COMPLETED'|'CANCELLED';

export type OrderResponseDto={
  id:number;
  auction_id:number;
  card_name:string;
  price:number;
  status:OrderStatus;
  created_at:string;
};

export type OrderDto={
  id:number;
  auctionId:number;
  cardName:string;
  price:number;
  status:OrderStatus;
  createdAt:string;
};
