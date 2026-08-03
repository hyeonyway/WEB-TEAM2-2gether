import {describe,expect,it} from 'vitest';
import type {NotificationDto,NotificationPageDto} from '../dto/notificationDto';
import {applyNotificationCreated} from './notificationStreamCache';

const notification:NotificationDto={
  id:9,auctionId:5,message:'상회 입찰이 발생했습니다.',isRead:false,createdAt:'2026-08-03T12:00:00',
};

function pageOf(items:NotificationDto[]):{pages:NotificationPageDto[];pageParams:(number|undefined)[]}{
  return {pages:[{items,nextCursor:null,hasNext:false}],pageParams:[undefined]};
}

describe('applyNotificationCreated',()=>{
  it('캐시가 없으면 아무것도 하지 않는다',()=>{
    expect(applyNotificationCreated(undefined,notification,false)).toBeUndefined();
  });

  it('첫 페이지 맨 앞에 알림을 추가한다',()=>{
    const current=pageOf([]);

    const result=applyNotificationCreated(current,notification,false);

    expect(result?.pages[0]?.items).toEqual([notification]);
  });

  it('unreadOnly 캐시엔 안읽은 알림을 추가한다',()=>{
    const current=pageOf([]);

    const result=applyNotificationCreated(current,notification,true);

    expect(result?.pages[0]?.items).toEqual([notification]);
  });

  it('unreadOnly 캐시엔 이미 읽은 알림을 추가하지 않는다',()=>{
    const current=pageOf([]);
    const read={...notification,isRead:true};

    const result=applyNotificationCreated(current,read,true);

    expect(result).toBe(current);
  });

  it('페이지가 비어있으면 그대로 반환한다',()=>{
    const current={pages:[],pageParams:[]};

    const result=applyNotificationCreated(current,notification,false);

    expect(result).toBe(current);
  });
});
