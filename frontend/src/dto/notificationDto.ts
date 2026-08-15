export type NotificationType=
  |'AUCTION_OPENED'|'OUTBID'|'AUCTION_WON'|'AUCTION_UNSOLD'|'ORDER_COMPLETED'|'ORDER_CANCELLED';

export type NotificationDto={
  id:number;
  auctionId:number;
  type:NotificationType;
  bidId:number;
  message:string;
  isRead:boolean;
  createdAt:string;
};

export type NotificationPageDto={
  items:NotificationDto[];
  nextCursor:number|null;
  hasNext:boolean;
};
