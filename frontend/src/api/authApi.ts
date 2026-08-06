import type {
  LoginRequestDto,
  LoginResponseDto,
  RefreshResponseDto,
  SessionLoginResponseDto,
  CurrentAccountResponseDto,
  SignupRequestDto,
  SignupResponseDto,
} from '../dto/authDto';
import {clearAccessToken, setAccessToken} from './accessTokenStore';
import {HttpError, request} from './httpClient';
import {isSessionAuthMode} from '../auth/authMode';
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
	if (isSessionAuthMode()) {
		const response = await request<SessionLoginResponseDto>('/api/auth/login', {
			...authRequestOptions, method: 'POST', body: JSON.stringify(loginRequest),
		});
		setCsrfToken(response.csrfToken);
		const current = await request<CurrentAccountResponseDto>('/api/auth/me', authRequestOptions);
		setSessionUserId(current.userId);
		return response;
	}
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
	if (isSessionAuthMode()) {
		try { await sessionAuthenticatedRequest<void>('/api/auth/logout', {method: 'POST'}); }
		finally { clearCsrfToken(); setSessionUserId(null); }
		return;
	}
  try {
    await request<void>('/api/auth/logout', {
      ...authRequestOptions,
      method: 'POST',
    });
  } finally {
    clearAccessToken();
  }
}
