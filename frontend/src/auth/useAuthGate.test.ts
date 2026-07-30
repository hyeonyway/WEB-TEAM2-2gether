import {describe, expect, it} from 'vitest';
import {sanitizeReturnTo} from './useAuthGate';

describe('sanitizeReturnTo', () => {
  it.each([
    ['/mypage', '/mypage'],
    ['/auction/17?tab=bid#history', '/auction/17?tab=bid#history'],
    ['https://evil.example/mypage', '/'],
    ['//evil.example/mypage', '/'],
    ['/mypage/../admin', '/'],
    ['/unknown', '/'],
  ])('%s를 안전한 내부 경로 %s로 변환한다', (returnTo, expected) => {
    expect(sanitizeReturnTo(returnTo)).toBe(expected);
  });
});
