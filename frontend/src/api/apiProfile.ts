export type ApiProfile='db'|'redis';

export function apiProfile():ApiProfile{
  return import.meta.env.VITE_API_PROFILE==='redis'?'redis':'db';
}

export function isRedisApiProfile():boolean{
  return apiProfile()==='redis';
}
