import {getDebugUserId} from './debugAuthStorage';

export class HttpError extends Error{
  constructor(public status:number,message:string,public code?:string){
    super(message);
  }
}

function parseErrorCode(body:string){
  try{
    const error=JSON.parse(body) as unknown;
    if(typeof error!=='object'||error===null)return undefined;
    const code=(error as {code?:unknown}).code;
    return typeof code==='string'?code:undefined;
  }catch{
    return undefined;
  }
}

export async function request<T>(path:string,options?:RequestInit):Promise<T>{
  const debugUserId=getDebugUserId();
  const headers=new Headers(options?.headers);
  if(!headers.has('Content-Type'))headers.set('Content-Type','application/json');
  if(headers.has('Authorization')){
    headers.delete('X-Debug-User-Id');
  }else if(debugUserId){
    headers.set('X-Debug-User-Id',debugUserId);
  }
  const response=await fetch(`${import.meta.env.VITE_API_BASE_URL??''}${path}`,{
    ...options,
    headers,
  });
  if(!response.ok){
    const body=await response.text();
    throw new HttpError(response.status,body,parseErrorCode(body));
  }
  if(response.status===204)return undefined as T;
  return response.json() as Promise<T>;
}
