export type NotificationDto={
  id:number;
  auctionId:number;
  message:string;
  isRead:boolean;
  createdAt:string;
};

export type NotificationPageDto={
  items:NotificationDto[];
  nextCursor:number|null;
  hasNext:boolean;
};
