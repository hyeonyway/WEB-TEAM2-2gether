import type {AuthTransport} from './authTransport';
import {sessionAuthTransport} from './session/sessionAuthTransport';

export function getAuthTransport(): AuthTransport {
  return sessionAuthTransport;
}
