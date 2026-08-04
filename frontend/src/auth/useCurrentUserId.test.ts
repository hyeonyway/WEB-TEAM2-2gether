import {act,renderHook} from '@testing-library/react';
import {afterEach,describe,expect,it} from 'vitest';
import {clearAccessToken,setAccessToken} from '../api/accessTokenStore';
import {useCurrentUserId} from './useCurrentUserId';

function fakeAccessToken(userId:number):string{
  const payload=btoa(JSON.stringify({sub:String(userId)}));
  return `header.${payload}.signature`;
}

describe('useCurrentUserId',()=>{
  afterEach(()=>clearAccessToken());

  it('토큰이 없으면 null이다',()=>{
    const{result}=renderHook(()=>useCurrentUserId());

    expect(result.current).toBeNull();
  });

  it('로그인하면 토큰의 userId를 반환한다',()=>{
    const{result}=renderHook(()=>useCurrentUserId());

    act(()=>setAccessToken(fakeAccessToken(42)));

    expect(result.current).toBe(42);
  });

  it('계정을 전환하면 새 userId로 갱신된다',()=>{
    setAccessToken(fakeAccessToken(1));
    const{result}=renderHook(()=>useCurrentUserId());

    act(()=>setAccessToken(fakeAccessToken(2)));

    expect(result.current).toBe(2);
  });

  it('로그아웃하면 다시 null이 된다',()=>{
    setAccessToken(fakeAccessToken(1));
    const{result}=renderHook(()=>useCurrentUserId());

    act(()=>clearAccessToken());

    expect(result.current).toBeNull();
  });
});
