import {useInfiniteQuery,useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useEffect} from 'react';
import {getDebugUserId} from '../api/debugAuthStorage';
import {useAuth} from '../auth/useAuth';
import {notificationMutations} from '../queries/notificationMutations';
import {notificationQueries,notificationQueryKeys} from '../queries/notificationQueries';

export function useNotifications(unreadOnly:boolean,enabled:boolean){
  const {status}=useAuth();
  const queryClient=useQueryClient();
  const isLoggedIn=status==='authenticated'||getDebugUserId()!==null;

  useEffect(()=>{
    if(status==='anonymous')queryClient.removeQueries({queryKey:notificationQueryKeys.all});
  },[status,queryClient]);

  const listQuery=useInfiniteQuery({...notificationQueries.list(unreadOnly),enabled:isLoggedIn&&enabled});
  const unreadCountQuery=useQuery({...notificationQueries.unreadCount(),enabled:isLoggedIn});

  const markAsReadMutation=useMutation(notificationMutations.markAsRead(queryClient));
  const markAllAsReadMutation=useMutation(notificationMutations.markAllAsRead(queryClient));

  const notifications=listQuery.data?.pages.flatMap(page=>page.items)??[];

  const refetchAll=()=>{
    if(!isLoggedIn)return;
    void listQuery.refetch();
    void unreadCountQuery.refetch();
  };

  return {
    isLoggedIn,
    notifications,
    unreadCount:unreadCountQuery.data??0,
    isLoading:listQuery.isPending,
    isError:listQuery.isError,
    hasNextPage:listQuery.hasNextPage??false,
    isFetchingNextPage:listQuery.isFetchingNextPage,
    fetchNextPage:()=>void listQuery.fetchNextPage(),
    refetchAll,
    markAsRead:(notificationId:number)=>markAsReadMutation.mutate(notificationId),
    markAllAsRead:()=>markAllAsReadMutation.mutate(),
  };
}