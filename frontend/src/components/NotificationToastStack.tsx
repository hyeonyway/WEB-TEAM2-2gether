import {useMutation,useQueryClient} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';
import type {NotificationDto} from '../dto/notificationDto';
import type {ToastState} from '../hooks/useNotificationToasts';
import {notificationMutations} from '../queries/notificationMutations';
import {getNotificationPath} from '../utils/notificationNavigation';

type NotificationToastStackProps={
  toasts:ToastState[];
  onDismiss:(id:number)=>void;
};

export default function NotificationToastStack({toasts,onDismiss}:NotificationToastStackProps){
  const navigate=useNavigate();
  const queryClient=useQueryClient();
  const markAsReadMutation=useMutation(notificationMutations.markAsRead(queryClient));

  if(!toasts.length)return null;

  const handleOpen=(notification:NotificationDto)=>{
    markAsReadMutation.mutate(notification.id);
    onDismiss(notification.id);
    navigate(getNotificationPath(notification));
  };

  return <div className="notification-toast-stack" aria-live="polite">
    {toasts.map(notification=>
      <div key={notification.id} className={`notification-toast${notification.isDismissing?' dismissing':''}`}>
        <button type="button" className="notification-toast-body" onClick={()=>handleOpen(notification)}>
          {notification.message}
        </button>
        <button
          type="button"
          className="notification-toast-close"
          aria-label="닫기"
          onClick={()=>onDismiss(notification.id)}
        >×</button>
      </div>,
    )}
  </div>;
}
