import {useSyncExternalStore} from 'react';
import {getSessionUserId,subscribeSessionUser} from './session/sessionAuthStore';

function getSnapshot():number|null{
	return getSessionUserId();
}

export function useCurrentUserId():number|null{
	return useSyncExternalStore(subscribeSessionUser,getSnapshot,getSnapshot);
}
