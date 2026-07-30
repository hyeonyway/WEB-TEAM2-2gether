import {infiniteQueryOptions,queryOptions} from '@tanstack/react-query';
import {fetchNotifications,fetchUnreadCount} from '../api/notificationApi';

export const notificationQueryKeys={
  all:['notifications'] as const,
  list:(unreadOnly:boolean)=>[...notificationQueryKeys.all,'list',unreadOnly] as const,
  unreadCount:['notifications','unread-count'] as const,
};

export const notificationQueries={
  list:(unreadOnly:boolean)=>infiniteQueryOptions({
    queryKey:notificationQueryKeys.list(unreadOnly),
    queryFn:({pageParam})=>fetchNotifications({cursor:pageParam,unreadOnly}),
    initialPageParam:undefined as number|undefined,
    getNextPageParam:lastPage=>lastPage.hasNext?lastPage.nextCursor??undefined:undefined,
    staleTime:30_000,
  }),
  unreadCount:()=>queryOptions({
    queryKey:notificationQueryKeys.unreadCount,
    queryFn:fetchUnreadCount,
    staleTime:30_000,
  }),
};
