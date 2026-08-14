import {describe,expect,it} from 'vitest';
import {
  SSE_RECONNECT_MAX_DELAY_MS,
  nextSseReconnectDelayMs,
  shouldRevalidateSession,
} from './sseReconnectPolicy';

describe('nextSseReconnectDelayMs',()=>{
  it('연속 실패마다 지연 시간이 2배씩 늘어난다',()=>{
    expect(nextSseReconnectDelayMs(1)).toBe(2_000);
    expect(nextSseReconnectDelayMs(2)).toBe(4_000);
    expect(nextSseReconnectDelayMs(3)).toBe(8_000);
    expect(nextSseReconnectDelayMs(4)).toBe(16_000);
  });

  it('상한을 넘지 않는다',()=>{
    expect(nextSseReconnectDelayMs(5)).toBe(SSE_RECONNECT_MAX_DELAY_MS);
    expect(nextSseReconnectDelayMs(20)).toBe(SSE_RECONNECT_MAX_DELAY_MS);
  });
});

describe('shouldRevalidateSession',()=>{
  it('임계치의 배수에서만 true를 반환한다',()=>{
    expect(shouldRevalidateSession(0)).toBe(false);
    expect(shouldRevalidateSession(4)).toBe(false);
    expect(shouldRevalidateSession(5)).toBe(true);
    expect(shouldRevalidateSession(9)).toBe(false);
    expect(shouldRevalidateSession(10)).toBe(true);
  });
});
