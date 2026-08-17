import {type ReactNode, useEffect, useRef} from 'react';
import {useNavigate} from 'react-router-dom';
import {showToast} from '../components/Toast';
import {useAuth} from './useAuth';

const ADMIN_REQUIRED_MESSAGE = '관리자 권한이 필요합니다';
const ADMIN_REQUIRED_TOAST_KEY = 'admin-required';

type RequireAdminProps = {
  children: ReactNode;
};

export function RequireAdmin({children}: RequireAdminProps) {
  const {status, role} = useAuth();
  const navigate = useNavigate();
  const blockedRef = useRef(false);
  const isAdmin = status === 'authenticated' && role === 'ADMIN';

  useEffect(() => {
    if (status === 'initializing' || isAdmin || blockedRef.current) return;
    blockedRef.current = true;
    showToast(ADMIN_REQUIRED_MESSAGE, ADMIN_REQUIRED_TOAST_KEY);
    navigate('/', {replace: true});
  }, [isAdmin, navigate, status]);

  if (isAdmin) {
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
