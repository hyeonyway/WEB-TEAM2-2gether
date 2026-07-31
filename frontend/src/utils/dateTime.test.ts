import {describe,expect,it} from 'vitest';
import {formatKoreanDate,formatKoreanMonthDay} from './dateTime';

describe('한국 시간 포맷',()=>{
  it('UTC 시각을 한국 날짜로 변환한다',()=>{
    expect(formatKoreanDate('2026-07-31T15:30:00Z')).toBe('2026. 08. 01.');
  });

  it('차트 날짜를 한국 월일로 표시한다',()=>{
    expect(formatKoreanMonthDay('2026-07-31T15:30:00Z')).toBe('08/01');
  });
});
