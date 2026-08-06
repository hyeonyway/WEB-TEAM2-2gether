import {
  createContext,
  type ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {getAccessToken, subscribeAccessToken} from '../api/accessTokenStore';
import {refreshAccessToken} from '../api/authApi';
import {isSessionAuthMode} from './authMode';
import {getSessionUserId, setSessionUserId, subscribeSessionUser} from './session/sessionAuthStore';
import {clearCsrfToken, setCsrfToken} from './session/csrfTokenStore';
import {request} from '../api/httpClient';
import type {CurrentAccountResponseDto, SessionLoginResponseDto} from '../dto/authDto';
import {decodeAccessTokenUserId} from '../api/jwtClaims';
import {useAuctionWalletSync} from '../hooks/useAuctionWalletSync';
import {useWalletCrossTabSync} from '../hooks/useWalletCrossTabSync';

export type AuthStatus = 'initializing' | 'authenticated' | 'anonymous';

export type AuthContextValue = {
  status: AuthStatus;
  retryInitialization: () => void;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

type AuthProviderProps = {
  children: ReactNode;
};

export function AuthProvider({children}: AuthProviderProps) {
  const queryClient = useQueryClient();
  const accessToken = useSyncExternalStore(
    subscribeAccessToken,
    getAccessToken,
    getAccessToken,
  );
  const sessionUserId = useSyncExternalStore(subscribeSessionUser, getSessionUserId, getSessionUserId);
  const [initialized, setInitialized] = useState(false);
  const initializationInFlightRef = useRef(false);

  const initialize = useCallback(async () => {
    if (initializationInFlightRef.current) return;
    initializationInFlightRef.current = true;
    setInitialized(false);
    try {
      if (isSessionAuthMode()) {
        const current = await request<CurrentAccountResponseDto>('/api/auth/me', {credentials: 'include'});
        const csrf = await request<SessionLoginResponseDto>('/api/auth/csrf', {credentials: 'include'});
        setSessionUserId(current.userId);
        setCsrfToken(csrf.csrfToken);
      } else {
        await refreshAccessToken();
      }
    } catch {
      if (isSessionAuthMode()) {
        setSessionUserId(null);
        clearCsrfToken();
      }
      // 인증 복구 실패는 anonymous 상태로 처리하고 전역 오류 UI는 노출하지 않는다.
    } finally {
      setInitialized(true);
      initializationInFlightRef.current = false;
    }
  }, []);

  useEffect(() => {
    void initialize();
  }, [initialize]);

  const status: AuthStatus = !initialized
    ? 'initializing'
    : (isSessionAuthMode() ? Boolean(sessionUserId) : Boolean(accessToken))
      ? 'authenticated'
      : 'anonymous';

  const currentUserId = isSessionAuthMode() ? sessionUserId : accessToken ? decodeAccessTokenUserId(accessToken) : null;
  useAuctionWalletSync(currentUserId, status === 'authenticated');
  useWalletCrossTabSync(status === 'authenticated');

  useEffect(() => {
    if (status !== 'anonymous') return;
    queryClient.removeQueries({queryKey: ['auth']});
    queryClient.removeQueries({queryKey: ['account']});
    queryClient.removeQueries({queryKey: ['wallet']});
  }, [queryClient, status]);

  const contextValue = useMemo<AuthContextValue>(() => ({
    status,
    retryInitialization: () => {
      void initialize();
    },
  }), [initialize, status]);

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
}
