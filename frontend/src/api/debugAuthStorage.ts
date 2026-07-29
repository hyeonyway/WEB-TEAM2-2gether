const DEBUG_USER_ID_KEY='DEBUG_USER_ID';

export function getDebugUserId():string|null{
  const value=localStorage.getItem(DEBUG_USER_ID_KEY)?.trim();
  if(!value)return null;
  const userId=Number(value);
  return Number.isInteger(userId)&&userId>0?String(userId):null;
}

export function setDebugUserId(userId:number):void{
  if(!Number.isInteger(userId)||userId<=0){
    throw new Error('디버그 사용자 ID는 양의 정수여야 합니다.');
  }
  localStorage.setItem(DEBUG_USER_ID_KEY,String(userId));
}

export function clearDebugUserId():void{
  localStorage.removeItem(DEBUG_USER_ID_KEY);
}
