import {type ReactNode, useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import AuthModal from '../components/auth/AuthModal';
import {useAuthGate} from './useAuthGate';

type RequireAuthProps = {
  children: ReactNode;
};

export function RequireAuth({children}: RequireAuthProps) {
  const location = useLocation();
  const authGate = useAuthGate();
  const returnTo = `${location.pathname}${location.search}${location.hash}`;

  useEffect(() => {
    if (authGate.status !== 'authenticated') {
      authGate.requestAccess(returnTo);
    }
  }, [authGate.requestAccess, authGate.status, returnTo]);

  if (authGate.status === 'authenticated') {
    return children;
  }
  if (authGate.status === 'initializing') {
    return (
      <main aria-busy="true">
        <p>인증 상태를 확인하고 있습니다.</p>
      </main>
    );
  }
  return (
    <AuthModal
      open={authGate.authModalOpen}
      onClose={authGate.cancelAuthentication}
      onLoginSuccess={authGate.completeAuthentication}
    />
  );
}
