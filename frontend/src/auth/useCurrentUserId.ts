import {useSyncExternalStore} from 'react';
import {getAccessToken,subscribeAccessToken} from '../api/accessTokenStore';
import {decodeAccessTokenUserId} from '../api/jwtClaims';

function getSnapshot():number|null{
  const accessToken=getAccessToken();
  return accessToken?decodeAccessTokenUserId(accessToken):null;
}

export function useCurrentUserId():number|null{
  return useSyncExternalStore(subscribeAccessToken,getSnapshot,getSnapshot);
}
