type Listener = () => void;
type SessionRole = 'USER' | 'ADMIN' | null;

let userId: number | null = null;
let role: SessionRole = null;
const listeners = new Set<Listener>();

export function getSessionUserId(): number | null { return userId; }
export function getSessionRole(): SessionRole { return role; }
// userId와 role은 항상 같은 /api/auth/me 응답에서 함께 나오므로 한 번에 갱신해서
// 로그인 전환 중 역할만 그대로 남는 상태가 생기지 않게 한다.
export function setSession(nextUserId: number | null, nextRole: SessionRole = null): void {
  userId = nextUserId;
  role = nextRole;
  listeners.forEach(listener => listener());
}
export function subscribeSessionUser(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
