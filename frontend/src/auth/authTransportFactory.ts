import {isSessionAuthMode} from './authMode';
import type {AuthTransport} from './authTransport';
import {jwtAuthTransport} from './jwt/jwtAuthTransport';
import {sessionAuthTransport} from './session/sessionAuthTransport';

export function getAuthTransport(): AuthTransport {
  return isSessionAuthMode() ? sessionAuthTransport : jwtAuthTransport;
}
