import {useCallback} from 'react';
import {showToast} from '../components/Toast';
import {useAuth} from './useAuth';

export const AUTH_REQUIRED_MESSAGE = '로그인이 필요합니다';
const AUTH_REQUIRED_TOAST_KEY = 'auth-required';

export function showAuthRequiredToast() {
  showToast(AUTH_REQUIRED_MESSAGE, AUTH_REQUIRED_TOAST_KEY);
}

export function useAuthGate() {
  const {status} = useAuth();

  const requestNavigation = useCallback(() => {
    if (status === 'authenticated') return true;
    if (status === 'anonymous') showAuthRequiredToast();
    return false;
  }, [status]);

  return {
    status,
    requestNavigation,
  };
}
