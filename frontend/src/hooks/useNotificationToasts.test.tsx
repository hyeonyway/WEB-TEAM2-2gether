import {act,renderHook} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import type {NotificationDto} from '../dto/notificationDto';
import {useNotificationToasts} from './useNotificationToasts';

function notification(id:number):NotificationDto{
  return {id,auctionId:10,message:`메시지 ${id}`,isRead:false,createdAt:'2026-08-03T12:00:00'};
}

describe('useNotificationToasts',()=>{
  beforeEach(()=>vi.useFakeTimers());
  afterEach(()=>vi.useRealTimers());

  it('push하면 토스트가 스택에 쌓인다',()=>{
    const{result}=renderHook(()=>useNotificationToasts());

    act(()=>result.current.push(notification(1)));
    act(()=>result.current.push(notification(2)));

    expect(result.current.toasts).toEqual([notification(1),notification(2)]);
  });

  it('dismiss하면 해당 알림만 제거되고 나머지는 남는다',()=>{
    const{result}=renderHook(()=>useNotificationToasts());
    act(()=>{
      result.current.push(notification(1));
      result.current.push(notification(2));
    });

    act(()=>result.current.dismiss(1));

    expect(result.current.toasts).toEqual([notification(2)]);
  });

  it('30초가 지나면 자동으로 사라진다',()=>{
    const{result}=renderHook(()=>useNotificationToasts());
    act(()=>result.current.push(notification(1)));

    act(()=>vi.advanceTimersByTime(30_000));

    expect(result.current.toasts).toEqual([]);
  });

  it('30초가 되기 전에는 사라지지 않는다',()=>{
    const{result}=renderHook(()=>useNotificationToasts());
    act(()=>result.current.push(notification(1)));

    act(()=>vi.advanceTimersByTime(29_000));

    expect(result.current.toasts).toEqual([notification(1)]);
  });

  it('같은 id를 다시 push해도 중복으로 쌓이지 않는다',()=>{
    const{result}=renderHook(()=>useNotificationToasts());

    act(()=>{
      result.current.push(notification(1));
      result.current.push(notification(1));
    });

    expect(result.current.toasts).toHaveLength(1);
  });

  it('dismiss 후에는 자동 소멸 타이머가 다시 실행되지 않는다',()=>{
    const{result}=renderHook(()=>useNotificationToasts());
    act(()=>result.current.push(notification(1)));
    act(()=>result.current.dismiss(1));
    act(()=>result.current.push(notification(2)));

    act(()=>vi.advanceTimersByTime(30_000));

    expect(result.current.toasts).toEqual([]);
  });

  it('clear하면 모든 토스트가 즉시 사라진다',()=>{
    const{result}=renderHook(()=>useNotificationToasts());
    act(()=>{
      result.current.push(notification(1));
      result.current.push(notification(2));
    });

    act(()=>result.current.clear());

    expect(result.current.toasts).toEqual([]);
  });
});
