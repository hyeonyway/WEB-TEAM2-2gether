import {getDebugUserId} from './debugAuthStorage';

export class HttpError extends Error{
  constructor(public status:number,message:string,public code?:string){
    super(message);
  }
}

type ApiErrorBody={code?:unknown;message?:unknown};

function parseErrorBody(body:string):{code?:string;message?:string}{
  try{
    const error=JSON.parse(body) as ApiErrorBody;
    if(typeof error!=='object'||error===null)return undefined;
    return {
      code:typeof error.code==='string'?error.code:undefined,
      message:typeof error.message==='string'&&error.message.trim()?error.message:undefined,
    };
  }catch{
    return {};
  }
}

function fallbackErrorMessage(status:number){
  if(status===400)return '요청 정보를 확인해 주세요.';
  if(status===401)return '인증이 필요합니다.';
  if(status===403)return '권한이 없습니다.';
  if(status===404)return '요청한 리소스를 찾을 수 없습니다.';
  return '요청 처리 중 오류가 발생했습니다.';
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
    const error=parseErrorBody(body);
    throw new HttpError(response.status,error.message??fallbackErrorMessage(response.status),error.code);
  }
  if(response.status===204)return undefined as T;
  return response.json() as Promise<T>;
}
