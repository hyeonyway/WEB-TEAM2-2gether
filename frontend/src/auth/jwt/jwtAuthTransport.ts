import {clearAccessToken, getAccessToken} from '../../api/accessTokenStore';
import {refreshAccessToken} from '../../api/authApi';
import {HttpError, request} from '../../api/httpClient';
import type {AuthTransport} from '../authTransport';

let refreshPromise: Promise<void> | null = null;

function refreshOnce() {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken()
      .then(() => undefined)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

function requestWithCurrentToken<T>(path: string, options?: RequestInit) {
  const headers = new Headers(options?.headers);
  const accessToken = getAccessToken();
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  return request<T>(path, {...options, headers});
}

async function jwtAuthenticatedRequest<T>(path: string, options?: RequestInit) {
  try {
    return await requestWithCurrentToken<T>(path, options);
  } catch (error) {
    if (!(error instanceof HttpError && error.status === 401)) throw error;
  }

  try {
    await refreshOnce();
  } catch (error) {
    clearAccessToken();
    throw error;
  }

  try {
    return await requestWithCurrentToken<T>(path, options);
  } catch (error) {
    if (error instanceof HttpError && error.status === 401) clearAccessToken();
    throw error;
  }
}

async function optionallyAuthenticatedJwtRequest<T>(path: string, options?: RequestInit) {
    if (!getAccessToken()) return request<T>(path, options);
    try {
      return await jwtAuthenticatedRequest<T>(path, options);
    } catch (error) {
      if (error instanceof HttpError && error.status === 401) return request<T>(path, options);
      throw error;
    }
}

export const jwtAuthTransport: AuthTransport = {
  request: jwtAuthenticatedRequest,
  optionallyAuthenticatedRequest: optionallyAuthenticatedJwtRequest,
};
