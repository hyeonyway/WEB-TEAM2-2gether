import type {
  LoginRequestDto,
  LoginResponseDto,
  RefreshResponseDto,
  SignupRequestDto,
  SignupResponseDto,
} from '../dto/authDto';
import {clearAccessToken, setAccessToken} from './accessTokenStore';
import {HttpError, request} from './httpClient';

const authRequestOptions = {
  credentials: 'include' as const,
};

export function signup(signupRequest: SignupRequestDto) {
  return request<SignupResponseDto>('/api/auth/signup', {
    ...authRequestOptions,
    method: 'POST',
    body: JSON.stringify(signupRequest),
  });
}

export async function login(loginRequest: LoginRequestDto) {
  const response = await request<LoginResponseDto>('/api/auth/login', {
    ...authRequestOptions,
    method: 'POST',
    body: JSON.stringify(loginRequest),
  });
  setAccessToken(response.accessToken);
  return response;
}

export async function refreshAccessToken() {
  try {
    const response = await request<RefreshResponseDto>('/api/auth/refresh', {
      ...authRequestOptions,
      method: 'POST',
    });
    setAccessToken(response.accessToken);
    return response;
  } catch (error) {
    if (error instanceof HttpError && error.status === 401) {
      clearAccessToken();
    }
    throw error;
  }
}

export async function logout() {
  try {
    await request<void>('/api/auth/logout', {
      ...authRequestOptions,
      method: 'POST',
    });
  } finally {
    clearAccessToken();
  }
}
