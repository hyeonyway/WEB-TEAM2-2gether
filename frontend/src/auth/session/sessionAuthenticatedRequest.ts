import {getCsrfToken} from './csrfTokenStore';
import {request} from '../../api/httpClient';

const unsafeMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export function sessionAuthenticatedRequest<T>(path: string, options?: RequestInit): Promise<T> {
  const headers = new Headers(options?.headers);
  if (unsafeMethods.has(options?.method?.toUpperCase() ?? 'GET')) {
    const csrfToken = getCsrfToken();
    if (csrfToken) headers.set('X-CSRF-Token', csrfToken);
  }
  return request<T>(path, {...options, credentials: 'include', headers});
}
