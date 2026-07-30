import {getDebugUserId} from './debugAuthStorage';

export class HttpError extends Error{
  constructor(public status:number,message:string){
    super(message);
  }
}

export async function request<T>(path:string,options?:RequestInit):Promise<T>{
  const debugUserId=getDebugUserId();
  const response=await fetch(`${import.meta.env.VITE_API_BASE_URL??''}${path}`,{
    ...options,
    headers:{
      'Content-Type':'application/json',
      ...(debugUserId?{'X-Debug-User-Id':debugUserId}:{ }),
      ...options?.headers,
    },
  });
  if(!response.ok)throw new HttpError(response.status,await response.text());
  if(response.status===204)return undefined as T;
  return response.json() as Promise<T>;
}
