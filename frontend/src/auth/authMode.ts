export type AuthMode = 'jwt' | 'session';

export function getAuthMode(): AuthMode {
  return import.meta.env.VITE_AUTH_MODE === 'session' ? 'session' : 'jwt';
}

export function isSessionAuthMode(): boolean {
  return getAuthMode() === 'session';
}
