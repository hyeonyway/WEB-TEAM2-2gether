export type OrderStatus='PENDING_CONFIRM'|'COMPLETED'|'CANCELLED';

export type OrderResponseDto={
  id:number|null;
  auction_id:number;
  card_name:string;
  price:number;
  status:OrderStatus;
  created_at:string;
  stream_id?:string|null;
  projection_status?:'PENDING'|'PROJECTED'|'ERROR';
};

export type OrderDto={
  id:number|null;
  auctionId:number;
  cardName:string;
  price:number;
  status:OrderStatus;
  createdAt:string;
  streamId:string|null;
  projectionStatus:'PENDING'|'PROJECTED'|'ERROR';
};
