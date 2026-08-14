import {act, renderHook} from '@testing-library/react';
import {afterEach, describe, expect, it} from 'vitest';
import {setSessionUserId} from './session/sessionAuthStore';
import {useCurrentUserId} from './useCurrentUserId';

describe('useCurrentUserId', () => {
  afterEach(() => setSessionUserId(null));

  it('세션 사용자가 없으면 null이다', () => {
    const {result} = renderHook(() => useCurrentUserId());

    expect(result.current).toBeNull();
  });

  it('세션 사용자 ID를 반환하고 계정 변경을 구독한다', () => {
    const {result} = renderHook(() => useCurrentUserId());

    act(() => setSessionUserId(42));
    expect(result.current).toBe(42);

    act(() => setSessionUserId(2));
    expect(result.current).toBe(2);
  });
});
