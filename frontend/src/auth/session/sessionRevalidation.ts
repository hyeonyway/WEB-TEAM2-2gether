import {request} from '../../api/httpClient';
import type {CurrentAccountResponseDto} from '../../dto/authDto';
import {clearCsrfToken} from './csrfTokenStore';
import {getSessionRole, getSessionUserId, setSession} from './sessionAuthStore';

export async function revalidateSession(): Promise<boolean> {
  try {
    const current = await request<CurrentAccountResponseDto>('/api/auth/me', {credentials: 'include'});
    if (getSessionUserId() !== current.userId || getSessionRole() !== current.role) {
      setSession(current.userId, current.role);
    }
    return true;
  } catch {
    setSession(null, null);
    clearCsrfToken();
    return false;
  }
}
