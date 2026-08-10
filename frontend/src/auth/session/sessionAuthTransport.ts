import {request} from '../../api/httpClient';
import type {AuthTransport} from '../authTransport';
import {sessionAuthenticatedRequest} from './sessionAuthenticatedRequest';

export const sessionAuthTransport: AuthTransport = {
  request: sessionAuthenticatedRequest,
  optionallyAuthenticatedRequest<T>(path: string, options?: RequestInit) {
    return request<T>(path, {...options, credentials: 'include'});
  },
};
