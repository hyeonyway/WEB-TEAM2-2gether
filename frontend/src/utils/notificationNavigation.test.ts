import {describe,expect,it} from 'vitest';
import type {NotificationDto} from '../dto/notificationDto';
import {getNotificationPath} from './notificationNavigation';

function notification(type:NotificationDto['type']):NotificationDto{
  return {id:1,auctionId:7,type,bidId:type==='OUTBID'?70:0,message:'메시지',isRead:false,createdAt:'2026-08-03T12:00:00'};
}

describe('getNotificationPath',()=>{
  it.each(['ORDER_COMPLETED','ORDER_CANCELLED']as const)(
    '%s 알림은 대시보드 주문 탭으로 이동한다',
    type=>{
      expect(getNotificationPath(notification(type))).toBe('/dashboard?tab=orders');
    },
  );

  it.each(['AUCTION_OPENED','OUTBID','AUCTION_WON','AUCTION_UNSOLD']as const)(
    '%s 알림은 경매 상세로 이동한다',
    type=>{
      expect(getNotificationPath(notification(type))).toBe('/auction/7');
    },
  );
});
