import {describe,expect,it} from 'vitest';
import {formatKoreanDate,formatKoreanMonthDay,formatLocalDate} from './dateTime';

describe('Asia/Seoul 고정 포맷 (백엔드 집계 날짜 표시용)',()=>{
  it('UTC 시각을 한국 날짜로 변환한다',()=>{
    expect(formatKoreanDate('2026-07-31T15:30:00Z')).toBe('2026. 08. 01.');
  });

  it('차트 날짜를 한국 월일로 표시한다',()=>{
    expect(formatKoreanMonthDay('2026-07-31T15:30:00Z')).toBe('08/01');
  });
});

describe('formatLocalDate (뷰어 로컬 타임존 표시용)',()=>{
  it('브라우저 로컬 타임존 기준으로 날짜 형식을 표시한다',()=>{
    // 실행 환경의 타임존에 따라 실제 날짜값은 달라질 수 있으므로(의도된 동작),
    // 정확한 값이 아니라 포맷 형태만 검증한다.
    expect(formatLocalDate('2026-07-31T15:30:00Z')).toMatch(/^\d{4}\. \d{2}\. \d{2}\.$/);
  });

  it('유효하지 않은 값은 원본 문자열을 그대로 반환한다',()=>{
    expect(formatLocalDate('not-a-date')).toBe('not-a-date');
  });
});
