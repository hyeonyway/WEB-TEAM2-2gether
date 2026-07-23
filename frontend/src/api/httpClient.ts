export class HttpError extends Error{
  constructor(public status:number,message:string){
    super(message);
  }
}

export async function request<T>(path:string,options?:RequestInit):Promise<T>{
  const response=await fetch(`${import.meta.env.VITE_API_BASE_URL??''}${path}`,{
    ...options,
    headers:{'Content-Type':'application/json',...options?.headers},
  });
  if(!response.ok)throw new HttpError(response.status,await response.text());
  return response.json() as Promise<T>;
}
