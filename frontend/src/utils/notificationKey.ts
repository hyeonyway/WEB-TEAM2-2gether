import type {NotificationDto} from '../dto/notificationDto';

export type NotificationKeyFields=Pick<NotificationDto,'type'|'auctionId'|'bidId'>;

export function notificationDedupKey(notification:NotificationKeyFields):string{
  return notification.type==='OUTBID'
    ?`OUTBID:${notification.bidId}`
    :`${notification.type}:${notification.auctionId}`;
}
