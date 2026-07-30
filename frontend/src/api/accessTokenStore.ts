let accessToken: string | null = null;
const listeners = new Set<() => void>();

function notify() {
  listeners.forEach(listener => listener());
}

export function getAccessToken() {
  return accessToken;
}

export function setAccessToken(nextAccessToken: string) {
  accessToken = nextAccessToken;
  notify();
}

export function clearAccessToken() {
  accessToken = null;
  notify();
}

export function subscribeAccessToken(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
