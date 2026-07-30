import {beforeEach, describe, expect, it} from 'vitest';
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from './accessTokenStore';

describe('accessTokenStore', () => {
  beforeEach(clearAccessToken);

  it('Access Token을 브라우저 저장소가 아닌 메모리에만 보관한다', () => {
    setAccessToken('access-token');

    expect(getAccessToken()).toBe('access-token');
    expect(localStorage.length).toBe(0);
  });

  it('Access Token을 제거한다', () => {
    setAccessToken('access-token');

    clearAccessToken();

    expect(getAccessToken()).toBeNull();
  });
});
