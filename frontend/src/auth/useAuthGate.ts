import {useCallback, useEffect, useState} from 'react';
import {matchPath, useNavigate} from 'react-router-dom';
import {appRoutePatterns} from '../app/routePaths';
import {useAuth} from './useAuth';

const INTERNAL_ORIGIN = 'https://dbidding.local';

export function sanitizeReturnTo(returnTo: string) {
  if (!returnTo.startsWith('/') || returnTo.startsWith('//')) return '/';
  try {
    const url = new URL(returnTo, INTERNAL_ORIGIN);
    if (url.origin !== INTERNAL_ORIGIN) return '/';
    if (!appRoutePatterns.some(path => matchPath({path, end: true}, url.pathname))) {
      return '/';
    }
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return '/';
  }
}

export function useAuthGate() {
  const {status} = useAuth();
  const navigate = useNavigate();
  const [pendingReturnTo, setPendingReturnTo] = useState<string | null>(null);
  const [authModalOpen, setAuthModalOpen] = useState(false);

  const requestAccess = useCallback((returnTo: string) => {
    if (status === 'authenticated') return true;

    setPendingReturnTo(sanitizeReturnTo(returnTo));
    if (status === 'anonymous') {
      setAuthModalOpen(true);
    }
    return false;
  }, [status]);

  useEffect(() => {
    if (!pendingReturnTo || authModalOpen) return;
    if (status === 'authenticated') {
      const destination = pendingReturnTo;
      setPendingReturnTo(null);
      navigate(destination);
    } else if (status === 'anonymous') {
      setAuthModalOpen(true);
    }
  }, [authModalOpen, navigate, pendingReturnTo, status]);

  const completeAuthentication = useCallback(() => {
    const destination = pendingReturnTo;
    setPendingReturnTo(null);
    setAuthModalOpen(false);
    if (destination) {
      navigate(destination);
    }
  }, [navigate, pendingReturnTo]);

  const cancelAuthentication = useCallback(() => {
    const protectedRequest = pendingReturnTo !== null;
    setPendingReturnTo(null);
    setAuthModalOpen(false);
    if (protectedRequest) {
      navigate('/');
    }
  }, [navigate, pendingReturnTo]);

  return {
    status,
    authModalOpen,
    requestAccess,
    completeAuthentication,
    cancelAuthentication,
  };
}
