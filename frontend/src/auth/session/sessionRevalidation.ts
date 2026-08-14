import {request} from '../../api/httpClient';
import type {CurrentAccountResponseDto} from '../../dto/authDto';
import {clearCsrfToken} from './csrfTokenStore';
import {getSessionUserId, setSessionUserId} from './sessionAuthStore';

export async function revalidateSession(): Promise<boolean> {
  try {
    const current = await request<CurrentAccountResponseDto>('/api/auth/me', {credentials: 'include'});
    if (getSessionUserId() !== current.userId) setSessionUserId(current.userId);
    return true;
  } catch {
    setSessionUserId(null);
    clearCsrfToken();
    return false;
  }
}
