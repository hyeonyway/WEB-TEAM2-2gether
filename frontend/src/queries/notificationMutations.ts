import type {InfiniteData,QueryClient} from '@tanstack/react-query';
import {mutationOptions} from '@tanstack/react-query';
import {markAllNotificationsAsRead,markNotificationAsRead} from '../api/notificationApi';
import type {NotificationPageDto} from '../dto/notificationDto';
import {notificationDedupKey,type NotificationKeyFields} from '../utils/notificationKey';
import {notificationQueryKeys} from './notificationQueries';

type ListSnapshot={unreadOnly:boolean;data:InfiniteData<NotificationPageDto>|undefined};

function snapshotNotifications(queryClient:QueryClient){
  return {
    lists:[true,false].map((unreadOnly):ListSnapshot=>({
      unreadOnly,
      data:queryClient.getQueryData<InfiniteData<NotificationPageDto>>(notificationQueryKeys.list(unreadOnly)),
    })),
    unreadCount:queryClient.getQueryData<number>(notificationQueryKeys.unreadCount),
  };
}

function restoreNotifications(queryClient:QueryClient,snapshot:ReturnType<typeof snapshotNotifications>){
  snapshot.lists.forEach(({unreadOnly,data})=>queryClient.setQueryData(notificationQueryKeys.list(unreadOnly),data));
  queryClient.setQueryData(notificationQueryKeys.unreadCount,snapshot.unreadCount);
}

function updateAllLists(queryClient:QueryClient,updateItems:(items:NotificationPageDto['items'])=>NotificationPageDto['items']){
  [true,false].forEach(unreadOnly=>{
    queryClient.setQueryData<InfiniteData<NotificationPageDto>>(
      notificationQueryKeys.list(unreadOnly),
      current=>current?{...current,pages:current.pages.map(page=>({
        ...page,
        items:unreadOnly?updateItems(page.items).filter(item=>!item.isRead):updateItems(page.items),
      }))}:current,
    );
  });
}

function settleNotifications(queryClient:QueryClient){
  void queryClient.invalidateQueries({queryKey:notificationQueryKeys.all});
}

export const notificationMutations={
  markAsRead:(queryClient:QueryClient)=>mutationOptions({
    mutationKey:['notifications','markAsRead'],
    mutationFn:(key:NotificationKeyFields)=>markNotificationAsRead(key),
    onMutate:async(key:NotificationKeyFields)=>{
      await queryClient.cancelQueries({queryKey:notificationQueryKeys.all});
      const snapshot=snapshotNotifications(queryClient);
      const dedupKey=notificationDedupKey(key);
      updateAllLists(queryClient,items=>items.map(item=>notificationDedupKey(item)===dedupKey&&!item.isRead?{...item,isRead:true}:item));
      const wasUnread=snapshot.lists.find(list=>!list.unreadOnly)?.data?.pages
        .flatMap(page=>page.items).find(item=>notificationDedupKey(item)===dedupKey)?.isRead===false;
      if(wasUnread)queryClient.setQueryData<number>(notificationQueryKeys.unreadCount,current=>current?Math.max(0,current-1):current);
      return snapshot;
    },
    onError:(_error,_key,snapshot)=>{
      if(snapshot)restoreNotifications(queryClient,snapshot);
    },
    onSettled:()=>settleNotifications(queryClient),
  }),
  markAllAsRead:(queryClient:QueryClient)=>mutationOptions({
    mutationKey:['notifications','markAllAsRead'],
    mutationFn:()=>markAllNotificationsAsRead(),
    onMutate:async()=>{
      await queryClient.cancelQueries({queryKey:notificationQueryKeys.all});
      const snapshot=snapshotNotifications(queryClient);
      updateAllLists(queryClient,items=>items.map(item=>({...item,isRead:true})));
      queryClient.setQueryData<number>(notificationQueryKeys.unreadCount,0);
      return snapshot;
    },
    onError:(_error,_variables,snapshot)=>{
      if(snapshot)restoreNotifications(queryClient,snapshot);
    },
    onSettled:()=>settleNotifications(queryClient),
  }),
};
