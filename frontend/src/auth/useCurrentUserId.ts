import {useSyncExternalStore} from 'react';
import {getAccessToken,subscribeAccessToken} from '../api/accessTokenStore';
import {decodeAccessTokenUserId} from '../api/jwtClaims';
import {isSessionAuthMode} from './authMode';
import {getSessionUserId,subscribeSessionUser} from './session/sessionAuthStore';

function getSnapshot():number|null{
	if(isSessionAuthMode())return getSessionUserId();
  const accessToken=getAccessToken();
  return accessToken?decodeAccessTokenUserId(accessToken):null;
}

export function useCurrentUserId():number|null{
	return useSyncExternalStore(isSessionAuthMode()?subscribeSessionUser:subscribeAccessToken,getSnapshot,getSnapshot);
}
