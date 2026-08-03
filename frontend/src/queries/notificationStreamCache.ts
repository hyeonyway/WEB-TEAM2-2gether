import type {InfiniteData} from '@tanstack/react-query';
import type {NotificationDto,NotificationPageDto} from '../dto/notificationDto';

export function applyNotificationCreated(
  current:InfiniteData<NotificationPageDto>|undefined,
  notification:NotificationDto,
  unreadOnly:boolean,
):InfiniteData<NotificationPageDto>|undefined{
  if(!current)return current;
  if(unreadOnly&&notification.isRead)return current;
  const[firstPage,...restPages]=current.pages;
  if(!firstPage)return current;
  return {...current,pages:[{...firstPage,items:[notification,...firstPage.items]},...restPages]};
}
