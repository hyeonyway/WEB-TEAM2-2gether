import {useEffect} from 'react';
import {ToastContainer} from '../components';
import NotificationToastStack from '../components/NotificationToastStack';
import {useAuth} from '../auth/useAuth';
import {useCurrentUserId} from '../auth/useCurrentUserId';
import {useMeStream} from '../hooks/useMeStream';
import {useNotificationToasts} from '../hooks/useNotificationToasts';
import {AppRoutes} from './router';

export default function App() {
  const {status} = useAuth();
  const userId = useCurrentUserId();
  const {toasts, push, dismiss, clear} = useNotificationToasts();
  useMeStream({enabled: status === 'authenticated', onNotificationCreated: push});

  useEffect(() => {
    clear();
  }, [userId, clear]);

  return (
    <>
      <AppRoutes/>
      <ToastContainer/>
      <NotificationToastStack toasts={toasts} onDismiss={dismiss}/>
    </>
  );
}
