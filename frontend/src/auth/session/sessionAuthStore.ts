type Listener = () => void;

let userId: number | null = null;
const listeners = new Set<Listener>();

export function getSessionUserId(): number | null { return userId; }
export function setSessionUserId(nextUserId: number | null): void {
  userId = nextUserId;
  listeners.forEach(listener => listener());
}
export function subscribeSessionUser(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
