import type {NotificationDto} from '../dto/notificationDto';

const ORDER_TAB_TYPES=new Set(['ORDER_COMPLETED','ORDER_CANCELLED']);

export function getNotificationPath(notification:NotificationDto):string{
  return ORDER_TAB_TYPES.has(notification.type)
    ? '/dashboard?tab=orders'
    : `/auction/${notification.auctionId}`;
}
