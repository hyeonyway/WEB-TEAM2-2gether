import {renderHook} from '@testing-library/react';
import type {ReactNode} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {describe, expect, it, vi} from 'vitest';
import {AuthContext, type AuthStatus} from './AuthProvider';
import {useAuthGate} from './useAuthGate';

function authWrapper(status: AuthStatus) {
  return function Wrapper({children}: {children: ReactNode}) {
    return (
      <MemoryRouter>
        <AuthContext.Provider value={{status, retryInitialization: vi.fn()}}>
          {children}
        </AuthContext.Provider>
      </MemoryRouter>
    );
  };
}

describe('useAuthGate', () => {
  it('authenticated 사용자의 접근을 허용한다', () => {
    const toastListener = vi.fn();
    window.addEventListener('app-toast', toastListener);
    const {result} = renderHook(() => useAuthGate(), {
      wrapper: authWrapper('authenticated'),
    });

    expect(result.current.requestNavigation()).toBe(true);
    expect(toastListener).not.toHaveBeenCalled();
    window.removeEventListener('app-toast', toastListener);
  });

  it('initializing 중에는 접근만 보류하고 안내하지 않는다', () => {
    const toastListener = vi.fn();
    window.addEventListener('app-toast', toastListener);
    const {result} = renderHook(() => useAuthGate(), {
      wrapper: authWrapper('initializing'),
    });

    expect(result.current.requestNavigation()).toBe(false);
    expect(toastListener).not.toHaveBeenCalled();
    window.removeEventListener('app-toast', toastListener);
  });

  it('anonymous 사용자의 접근을 막고 로그인 필요 토스트를 표시한다', () => {
    const toastListener = vi.fn();
    window.addEventListener('app-toast', toastListener);
    const {result} = renderHook(() => useAuthGate(), {
      wrapper: authWrapper('anonymous'),
    });

    expect(result.current.requestNavigation()).toBe(false);
    expect(toastListener).toHaveBeenCalledOnce();
    expect((toastListener.mock.calls[0]?.[0] as CustomEvent).detail.message)
      .toBe('로그인이 필요합니다');
    window.removeEventListener('app-toast', toastListener);
  });
});
