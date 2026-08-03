import {type ReactNode, useEffect, useRef} from 'react';
import {useNavigate} from 'react-router-dom';
import {showAuthRequiredToast} from './useAuthGate';
import {useAuth} from './useAuth';

type RequireAuthProps = {
  children: ReactNode;
};

export function RequireAuth({children}: RequireAuthProps) {
  const {status} = useAuth();
  const navigate = useNavigate();
  const blockedRef = useRef(false);

  useEffect(() => {
    if (status !== 'anonymous' || blockedRef.current) return;
    blockedRef.current = true;
    showAuthRequiredToast();
    navigate('/', {replace: true});
  }, [navigate, status]);

  if (status === 'authenticated') {
    return children;
  }
  if (status === 'initializing') {
    return (
      <main aria-busy="true">
        <p>인증 상태를 확인하고 있습니다.</p>
      </main>
    );
  }
  return null;
}
