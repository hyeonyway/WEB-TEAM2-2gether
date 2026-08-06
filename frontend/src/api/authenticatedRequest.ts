import {getAuthTransport} from '../auth/authTransportFactory';

export function authenticatedRequest<T>(path: string, options?: RequestInit) {
  return getAuthTransport().request<T>(path, options);
}

export function optionallyAuthenticatedRequest<T>(path: string, options?: RequestInit) {
  return getAuthTransport().optionallyAuthenticatedRequest<T>(path, options);
}
