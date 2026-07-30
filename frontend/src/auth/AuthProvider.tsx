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
import {HttpError} from '../api/httpClient';

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
  const [initialized, setInitialized] = useState(false);
  const [recoveryError, setRecoveryError] = useState(false);
  const initializationInFlightRef = useRef(false);

  const initialize = useCallback(async () => {
    if (initializationInFlightRef.current) return;
    initializationInFlightRef.current = true;
    setInitialized(false);
    setRecoveryError(false);
    try {
      await refreshAccessToken();
    } catch (error) {
      if (!(error instanceof HttpError && error.status === 401)) {
        setRecoveryError(true);
      }
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
    : accessToken
      ? 'authenticated'
      : 'anonymous';

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
      {recoveryError && (
        <div role="alert">
          <span>로그인 상태를 확인하지 못했습니다.</span>
          <button type="button" onClick={() => void initialize()}>다시 시도</button>
        </div>
      )}
      {children}
    </AuthContext.Provider>
  );
}
