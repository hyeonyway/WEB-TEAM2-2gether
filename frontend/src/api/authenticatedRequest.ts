import {clearAccessToken, getAccessToken} from './accessTokenStore';
import {refreshAccessToken} from './authApi';
import {HttpError, request} from './httpClient';

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
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return request<T>(path, {
    ...options,
    headers,
  });
}

export async function authenticatedRequest<T>(path: string, options?: RequestInit) {
  try {
    return await requestWithCurrentToken<T>(path, options);
  } catch (error) {
    if (!(error instanceof HttpError && error.status === 401)) {
      throw error;
    }
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
    if (error instanceof HttpError && error.status === 401) {
      clearAccessToken();
    }
    throw error;
  }
}

export async function optionallyAuthenticatedRequest<T>(path: string, options?: RequestInit) {
  if (!getAccessToken()) return request<T>(path, options);

  try {
    return await authenticatedRequest<T>(path, options);
  } catch (error) {
    if (error instanceof HttpError && error.status === 401) {
      return request<T>(path, options);
    }
    throw error;
  }
}
