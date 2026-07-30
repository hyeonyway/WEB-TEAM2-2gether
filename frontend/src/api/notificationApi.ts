import {request} from './httpClient';
import {isMockApiEnabled} from './mockApiConfig';
import type {NotificationDto,NotificationPageDto} from '../dto/notificationDto';

const MOCK_STORAGE_KEY='mock-notifications';

const MOCK_SEED:NotificationDto[]=[
  {id:5,auctionId:12,message:'찜한 카드 "피카츄 P 메가 에볼루션 프로모카드"의 경매가 종료 임박입니다.',isRead:false,createdAt:'2026-07-30T09:12:00'},
  {id:4,auctionId:9,message:'회원님의 입찰이 상회되었습니다.',isRead:false,createdAt:'2026-07-29T22:40:00'},
  {id:3,auctionId:9,message:'경매가 낙찰되었습니다.',isRead:true,createdAt:'2026-07-28T14:05:00'},
  {id:2,auctionId:3,message:'찜한 카드의 경매가 등록되었습니다.',isRead:true,createdAt:'2026-07-27T11:00:00'},
  {id:1,auctionId:1,message:'찜한 카드의 경매가 등록되었습니다.',isRead:true,createdAt:'2026-07-25T08:30:00'},
];

function readMockNotifications():NotificationDto[]{
  try{
    const value=JSON.parse(localStorage.getItem(MOCK_STORAGE_KEY)??'null');
    if(Array.isArray(value))return value;
  }catch{
    // fall through to reseed
  }
  writeMockNotifications(MOCK_SEED);
  return MOCK_SEED;
}

function writeMockNotifications(notifications:NotificationDto[]):void{
  localStorage.setItem(MOCK_STORAGE_KEY,JSON.stringify(notifications));
}

function sliceMockPage(notifications:NotificationDto[],cursor:number|undefined,size:number):NotificationPageDto{
  const sorted=[...notifications].sort((a,b)=>b.id-a.id);
  const startIndex=cursor==null?0:sorted.findIndex(item=>item.id<cursor);
  const from=startIndex===-1?sorted.length:startIndex;
  const page=sorted.slice(from,from+size);
  const hasNext=from+size<sorted.length;
  return {
    items:page,
    nextCursor:hasNext?page[page.length-1]?.id??null:null,
    hasNext,
  };
}

export async function fetchNotifications(params:{cursor?:number;size?:number;unreadOnly?:boolean}):Promise<NotificationPageDto>{
  const size=params.size??20;
  if(isMockApiEnabled()){
    const all=readMockNotifications();
    const filtered=params.unreadOnly?all.filter(item=>!item.isRead):all;
    return sliceMockPage(filtered,params.cursor,size);
  }
  const query=new URLSearchParams();
  if(params.cursor!=null)query.set('cursor',String(params.cursor));
  query.set('size',String(size));
  if(params.unreadOnly)query.set('read','false');
  return request<NotificationPageDto>(`/api/notifications?${query}`);
}

export async function fetchUnreadCount():Promise<number>{
  if(isMockApiEnabled()){
    return readMockNotifications().filter(item=>!item.isRead).length;
  }
  const {count}=await request<{count:number}>('/api/notifications/unread-count');
  return count;
}

export async function markNotificationAsRead(notificationId:number):Promise<void>{
  if(isMockApiEnabled()){
    writeMockNotifications(readMockNotifications().map(item=>item.id===notificationId?{...item,isRead:true}:item));
    return;
  }
  await request<void>(`/api/notifications/${notificationId}/read`,{method:'PATCH'});
}

export async function markAllNotificationsAsRead():Promise<void>{
  if(isMockApiEnabled()){
    writeMockNotifications(readMockNotifications().map(item=>({...item,isRead:true})));
    return;
  }
  await request<void>('/api/notifications/read-all',{method:'PATCH'});
}
