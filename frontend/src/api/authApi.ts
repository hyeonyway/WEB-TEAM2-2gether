import type {
  LoginRequestDto,
  SessionLoginResponseDto,
  CurrentAccountResponseDto,
  MyWarningSummaryDto,
  SignupRequestDto,
  SignupResponseDto,
} from '../dto/authDto';
import {request} from './httpClient';
import {authenticatedRequest} from './authenticatedRequest';
import {clearCsrfToken, setCsrfToken} from '../auth/session/csrfTokenStore';
import {setSessionUserId} from '../auth/session/sessionAuthStore';
import {sessionAuthenticatedRequest} from '../auth/session/sessionAuthenticatedRequest';

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
  const response = await request<SessionLoginResponseDto>('/api/auth/login', {
    ...authRequestOptions,
    method: 'POST',
    body: JSON.stringify(loginRequest),
  });
  setCsrfToken(response.csrfToken);
  const current = await request<CurrentAccountResponseDto>('/api/auth/me', authRequestOptions);
  setSessionUserId(current.userId);
  return response;
}

export async function logout() {
  try { await sessionAuthenticatedRequest<void>('/api/auth/logout', {method: 'POST'}); }
  finally { clearCsrfToken(); setSessionUserId(null); }
}

export function fetchMyWarningSummary() {
  return authenticatedRequest<MyWarningSummaryDto>('/api/auth/me/warnings');
}
