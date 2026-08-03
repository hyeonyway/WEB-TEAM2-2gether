export function decodeAccessTokenUserId(accessToken:string):number|null{
  const payload=accessToken.split('.')[1];
  if(!payload)return null;
  try{
    const json=JSON.parse(atob(payload.replace(/-/g,'+').replace(/_/g,'/')));
    const userId=Number(json.sub);
    return Number.isInteger(userId)&&userId>0?userId:null;
  }catch{
    return null;
  }
}
