import {useEffect} from 'react';
import {ToastContainer} from '../components';
import NotificationToastStack from '../components/NotificationToastStack';
import {useAuth} from '../auth/useAuth';
import {useNotificationStream} from '../hooks/useNotificationStream';
import {useNotificationToasts} from '../hooks/useNotificationToasts';
import {AppRoutes} from './router';

export default function App() {
  const {status} = useAuth();
  const {toasts, push, dismiss, clear} = useNotificationToasts();
  useNotificationStream({enabled: status === 'authenticated', onNotificationCreated: push});

  useEffect(() => {
    if (status !== 'authenticated') clear();
  }, [status, clear]);

  return (
    <>
      <AppRoutes/>
      <ToastContainer/>
      <NotificationToastStack toasts={toasts} onDismiss={dismiss}/>
    </>
  );
}
